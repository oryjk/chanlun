# 架构边界收紧 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变任何对外行为（API 字段、持久化格式、页面表现）的前提下，把核心分析链路、时钟、组合根、榜单任务端口收紧到整洁架构的边界内，并用自动化测试防止越层依赖回退。

**Architecture:** 缠论分析链路从应用层查询中抽成领域服务 `MarketAnalysisPipeline`，查询与榜单命令都调用它；墙钟改为应用层 `Clock` 端口；`app/container.py` 只保留一个组合入口，所有适配器单例共享；榜单的任务状态（进度、检查点）移出领域层，巨型 `RankingWriter` 按职责拆成三个端口；前端网关显式映射榜单 DTO。

**Tech Stack:** Python 3 + FastAPI + unittest（`uv run`），SQLite；React 19 + Vite，测试用 `bun test`。

**Spec:** 本计划依据的审阅结论记录在本文件"背景"一节；项目规则以仓库根目录 `AGENTS.md` 为准，执行前必须先读完。

## 背景（审阅结论摘要）

import 方向已全部向内，没有反向依赖。需要修的问题如下，括号内为对应任务：

1. 缠论完整分析链路（分型笔中枢 → guidance → MACD → 买卖点 → 信号质量）写在 `GetMarketSnapshotQuery._build_snapshot` 里，`BuildDailyRankingsCommand` 直接依赖这个具体查询类。新服务（回测、推送、自选扫描）只能借用一个查询用例。（Task 2）
2. `get_market_snapshot.py` 直接调用 `datetime.now()`，判断 K 线是否完成依赖运行时刻，无法测试和回放。（Task 2）
3. `container.py` 的 `build_ranking_worker` 另建了一套 `SqliteRankingStore`、HTTP 客户端和行情链，而且这条链里的新浪读取器没有前复权因子和名称查询，配置与主链不一致。（Task 3）
4. `RankingWriter` 一个端口有 8 个方法，混合了租约、检查点和发布；`RankingProgress`、`RankingItem`、`RankingView` 是后台任务状态，却放在 `domain/rankings.py`；`StockPoolReader` 端口已经没有调用方。（Task 4）
5. 前端 `marketGateway.getRankings` 直接 `...entry` 透传后端字段，`infrastructure/api` 没有起到 DTO 边界作用。（Task 5）
6. 没有自动检查依赖方向的测试。（Task 1）

**不在本计划内，需另立计划：**
- **证券标识去东方财富化**（领域层 `validate_security_id` 写死 `1.600519` 格式）。这会牵涉 API 契约、SQLite 快照、`var/quotes` 文件名、前端路由和小程序回跳参数。需要用户先定对外标识格式和旧链接兼容策略，再单独写计划。
- **领域层中文文案改为原因码**（`TimeFrame.label`、`BuySellPoint.reason`、`SignalQuality.reasons`）。这会改持久化快照和 API 字段，收益主要在多语言和多端，目前没有需求。
- 美股相关问题不处理（用户确认暂不需要美股）。

## Global Constraints

- 开始前工作区必须干净：`git status --short` 无输出。如果还有未提交的"稳健信号"改动，停下来请用户先提交，不要把两批改动混在一起。
- 直接在 `main` 分支开发，不建功能分支（AGENTS.md 第六节）。
- 只在用户明确授权后才执行各任务的 Commit 步骤；未授权时完成该任务验证后停下汇报，不提交、不推送。
- 不改变任何对外行为：`/api/*` 的请求参数、响应字段名和取值、SQLite 快照 JSON 结构、`var/quotes` 文件格式、页面文案都保持原样。`ANALYSIS_VERSION` 保持 `4`，不得升级。
- 不读写、不删除 `var/` 下的运行数据；测试一律用临时目录。
- 不新增第三方依赖（后端不加 import-linter，前端不加 ESLint）；架构守护测试只用标准库或 bun 内置 API 实现。
- 不创建兼容转发模块或重导出（例如在 `domain/rankings.py` 里重导出 `RankingProgress`）；移动后直接更新所有调用方。
- 每个任务改了目录职责时，同步更新 `AGENTS.md` 第二节或第三节的目录说明（AGENTS.md 第五节要求）。
- 后端测试命令：`uv run python -m unittest discover -s tests`；前端测试命令：`cd frontend && bun test src`。当前基线为后端 128 项、前端 33 项全部通过。

## Review Focus

以下情况各任务的测试没有覆盖到，但最容易在实际使用中出问题：

1. **时钟必须带时区。** `SystemClock.now()` 如果返回 naive datetime，`SignalQualityEvaluator._completed` 里的 `astimezone` 会按服务器本地时区解释，在非上海时区的部署机上会把盘中 K 线判成已完成。→ Task 2 增加 `test_system_clock_returns_aware_utc`。
2. **关闭榜单后不能启动后台线程。** `CHANLUN_RANKING_ENABLED=false` 时，合并组合根后不能创建或启动 worker，否则隔离测试会写 SQLite、访问外网。→ Task 3 增加 `test_disabled_ranking_builds_no_worker`。
3. **旧快照必须照常解码。** 任务状态类换模块后，SQLite 里已有的快照和检查点仍要能读出来。持久化 JSON 不含类路径，理论上不受影响，但要有测试证明。→ Task 4 依赖并保留 `tests/test_analysis_versions.py` 中缺版本字段的旧数据解码用例，不得删改其断言。
4. **前端网关要兼容旧后端响应。** 前端可能先于后端部署，旧后端返回的榜单缺少 `signal_quality` 和 `signal_group`。显式映射后必须给出 `null` 和 `'observation'` 默认值，不能抛错。→ Task 5 的 `getRankings defaults fields missing from older backends`。
5. **多级别联立在构造参数变化后仍要正常。** `GetSignalConfluenceQuery` 依赖快照查询，查询构造参数改变后，四个级别仍要正常返回，单个级别失败时仍跳过该级别。→ Task 6 的真实 HTTP 检查必须包含 `/api/chan/confluence`。

---

### Task 1: 架构守护测试

**Files:**
- Create: `tests/test_architecture.py`
- Create: `frontend/src/architecture.test.js`
- Modify: `AGENTS.md`（第五节变更检查补一句：分层依赖由 `tests/test_architecture.py` 与 `frontend/src/architecture.test.js` 自动检查）

**Interfaces:**
- Produces: `find_violations(sources: dict[str, str]) -> list[str]`（`tests/test_architecture.py` 内的模块级函数）。参数键为相对仓库根的路径（如 `backend/domain/x.py`），值为源码；返回 `"<path>:<line> imports <module>"` 形式的违规列表。Task 2 会往这个文件追加测试。

后端规则。把相对导入解析成 `backend.xxx` 的绝对包名后，按顶层层名判断：

| 所在层 | 禁止导入 |
|---|---|
| `backend.domain` | `backend.application`、`backend.infrastructure`、`backend.interfaces`、`backend.app`、`fastapi`、`pydantic`、`sqlite3`、`urllib`、`http`、`socket`、`threading`、`concurrent` |
| `backend.application` | `backend.infrastructure`、`backend.interfaces`、`backend.app`、`fastapi`、`pydantic`、`sqlite3`、`urllib`、`http`、`socket` |
| `backend.infrastructure` | `backend.interfaces`、`backend.app` |
| `backend.interfaces` | `backend.infrastructure`、`backend.app` |

前端规则。解析 `src/` 下 `.js`、`.jsx` 文件中的 `from '...'` 和 `import('...')`，把相对路径解析成 `src` 下的目录：

| 所在位置 | 禁止导入 |
|---|---|
| `domain/` | `application/`、`infrastructure/`、`presentation/`、`app/`、`react`、`echarts` |
| `application/` | `infrastructure/`、`presentation/`、`app/`、`react` |
| `infrastructure/` | `presentation/`、`app/` |
| `presentation/` 与 `App.jsx` | `infrastructure/`、`app/` |

另外，`fetch(` 只允许出现在 `infrastructure/` 下。`main.jsx`、`app/` 和 `*.test.js` 不受上述规则约束，但 `*.test.js` 仍不得被生产代码导入。

- [ ] **Step 1: 写后端守护的自检测试（先失败）**

```python
def test_guard_detects_relative_import_violation(self):
    sources = {"backend/domain/services/x.py": "from ...infrastructure.http.json_client import JsonHttpClient\n"}
    self.assertEqual(find_violations(sources),
                     ["backend/domain/services/x.py:1 imports backend.infrastructure.http.json_client"])

def test_guard_allows_inward_imports(self):
    sources = {"backend/application/queries/q.py": "from ...domain.models import Stock\nfrom ..ports.readers import X\n"}
    self.assertEqual(find_violations(sources), [])

def test_layer_dependencies_point_inward(self):
    sources = {str(p): p.read_text(encoding="utf-8") for p in Path("backend").rglob("*.py")}
    self.assertEqual(find_violations(sources), [])
```

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_architecture -v`
Expected: FAIL / ERROR，`find_violations` 未定义

- [ ] **Step 3: 在 `tests/test_architecture.py` 实现 `find_violations`**

用 `ast.parse` 遍历 `Import` 和 `ImportFrom`。相对导入按 `level` 从文件所在包向上解析；违规判断按 `module == 禁止项 or module.startswith(禁止项 + ".")`。

- [ ] **Step 4: 运行确认通过**

Run: `uv run python -m unittest tests.test_architecture -v`
Expected: 3 项 PASS（当前代码没有违规；如果 `test_layer_dependencies_point_inward` 报出真实违规，停下汇报，不要放宽规则）

- [ ] **Step 5: 写前端守护测试**

在 `frontend/src/architecture.test.js` 写：
- `test('guard flags presentation importing infrastructure')`：对内存源码 `{ 'presentation/components/X.jsx': "import { a } from '../../infrastructure/api/marketGateway.js'" }` 调用模块内 `findViolations(sources)`，期望结果为 `['presentation/components/X.jsx imports infrastructure/api/marketGateway.js']`。
- `test('src layers depend inward')`：用 `node:fs` 递归读取 `src/`，期望 `findViolations(...)` 为 `[]`。
- `test('only infrastructure calls fetch')`：期望不存在 `infrastructure/` 以外、非测试文件中含 `fetch(` 的文件。

- [ ] **Step 6: 运行前端测试**

Run: `cd frontend && bun test src`
Expected: 全部 PASS（33 + 3 项）

- [ ] **Step 7: 更新 AGENTS.md 第五节并提交**

```bash
git add tests/test_architecture.py frontend/src/architecture.test.js AGENTS.md
git commit -m "test: guard clean-architecture layer dependencies"
```

---

### Task 2: 抽出领域分析链路并注入时钟

**Files:**
- Create: `backend/domain/services/market_analysis.py`
- Create: `backend/application/ports/clock.py`
- Create: `backend/infrastructure/clock.py`
- Modify: `backend/application/queries/get_market_snapshot.py`（整体瘦身）
- Modify: `backend/application/commands/build_daily_rankings.py:1-37`（构造参数与 `_analyze`）
- Modify: `backend/app/container.py`（装配）
- Modify（仅更新构造调用）: `tests/test_chan_analyzer.py:208`、`tests/test_full_market_rankings.py:65`、`tests/test_daily_ranking_command.py:50,84-89`、`tests/test_ranking_api.py:66`、`tests/test_rankings.py:93`
- Create: `tests/test_market_analysis.py`
- Modify: `tests/test_architecture.py`（追加一项）
- Modify: `AGENTS.md`（第二节 `domain/services` 说明补"市场分析链路"，`application/ports` 补 `clock.py`）

**Interfaces:**
- Produces:
  - `backend/domain/services/market_analysis.py`
    - `class MarketAnalysisPipeline`
      - `__init__(self, analyzer: ChanAnalyzer, guidance: StructureGuidanceEvaluator, signals: ChanSignalAnalyzer, quality: SignalQualityEvaluator)`：四个参数都必填，`quality` 不再可选。
      - `analyze(self, market_data: MarketData, timeframe: TimeFrame, *, as_of: datetime) -> MarketSnapshot`：原 `_build_snapshot` 与 `_find_previous_close` 原样搬入，`datetime.now(...)` 改为 `as_of`。
    - `def close_window(market_data: MarketData, through_date: date) -> MarketData`：原 `execute` 中 `through_date` 分支原样搬入，包括错误文案"该证券的有效收盘行情不足，无法进行分析"和异常类型 `MarketDataNotFound`。
  - `backend/application/ports/clock.py`：`class Clock(Protocol): def now(self) -> datetime: ...`（约定返回带时区的 UTC 时间）。
  - `backend/infrastructure/clock.py`：`class SystemClock` 实现 `now()`，返回 `datetime.now(timezone.utc)`。
  - `GetMarketSnapshotQuery.__init__(self, reader: MarketDataReader, pipeline: MarketAnalysisPipeline, clock: Clock)`
  - `GetMarketSnapshotQuery.execute(self, secid: str, timeframe: str, refresh: bool = False) -> MarketSnapshot`：去掉 `through_date` 参数，它只被命令使用。
  - `BuildDailyRankingsCommand.__init__(self, universe: StockUniverseReader, market_data: MarketDataReader, pipeline: MarketAnalysisPipeline, reader: RankingReader, writer: RankingWriter, clock: Clock, *, workers=4, retries=2, lease_seconds=180)`：去掉 `now=` 参数，内部所有 `self._now()` 改为 `self._clock.now()`。Task 4 会再次修改这个签名。
  - `_analyze` 内部改为：`pipeline.analyze(close_window(market_data.read(stock.secid, TimeFrame.ONE_DAY, force_refresh=True), trade_date), TimeFrame.ONE_DAY, as_of=self._clock.now())`，`build_ranking_entry` 与重试逻辑不变。
- 测试中的固定时钟就地定义：`class FixedClock: def __init__(self, value): self.value = value; def now(self): return self.value`。

- [ ] **Step 1: 写失败测试 `tests/test_market_analysis.py`**

K 线夹具复用 `tests/test_chan_analyzer.py` 构造 `Candle` 的方式，时区为 `ZoneInfo("Asia/Shanghai")`，最后一根为 2026-11-10 的日线。

```python
def test_close_window_drops_later_bars_and_keeps_trade_date(self):
    data = market_data(days=["2026-11-06", "2026-11-09", "2026-11-10"])
    window = close_window(data, date(2026, 11, 9))
    self.assertEqual(window.candles[-1].time.date(), date(2026, 11, 9))
    self.assertEqual(len(window.candles), len(data.candles) - 1)

def test_close_window_requires_a_bar_on_the_trade_date(self):
    data = market_data(days=["2026-11-06", "2026-11-10"])
    with self.assertRaises(MarketDataNotFound):
        close_window(data, date(2026, 11, 9))

def test_snapshot_completion_follows_the_injected_clock(self):
    # 120 根日线、末根 2026-11-10，确保产生至少一个买卖点（复用 test_signal_quality 中能产出买卖点的序列）
    reader = SimpleNamespace(read=lambda *a, **k: data_with_points)
    intraday = datetime(2026, 11, 10, 2, 0, tzinfo=timezone.utc)   # 北京 10:00
    after_close = datetime(2026, 11, 10, 7, 30, tzinfo=timezone.utc)  # 北京 15:30
    q1 = GetMarketSnapshotQuery(reader, pipeline(), FixedClock(intraday)).execute("1.600000", "1d")
    q2 = GetMarketSnapshotQuery(reader, pipeline(), FixedClock(after_close)).execute("1.600000", "1d")
    self.assertEqual(q1.signal_quality.state, "insufficient")
    self.assertNotEqual(q2.signal_quality.reasons[0], "当前 K 线尚未完成，等待收盘后评价")

def test_system_clock_returns_aware_utc(self):
    now = SystemClock().now()
    self.assertIsNotNone(now.tzinfo)
    self.assertEqual(now.utcoffset(), timedelta(0))
```

在 `tests/test_architecture.py` 追加：

```python
def test_core_layers_do_not_read_the_wall_clock(self):
    offenders = [str(p) for layer in ("backend/domain", "backend/application")
                 for p in Path(layer).rglob("*.py") if "datetime.now(" in p.read_text(encoding="utf-8")]
    self.assertEqual(offenders, [])
```

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_market_analysis tests.test_architecture -v`
Expected: 前者 ImportError（模块不存在）；`test_core_layers_do_not_read_the_wall_clock` FAIL，列出 `get_market_snapshot.py` 和 `build_daily_rankings.py`

- [ ] **Step 3: 实现**

按 Interfaces 新建三个模块，瘦身查询，改造命令。在 `container.py` 里创建一个 `SystemClock()` 和一个 `MarketAnalysisPipeline(...)`，注入查询和命令；命令改用共享的 `market_data`，不再接收 `snapshot_query`。然后更新 Files 中列出的 5 个测试文件的构造调用：
- 原来传 `snapshots` 假对象的地方，改传一个 `read()` 返回 `MarketData` 的假读取器，加上真实的 `MarketAnalysisPipeline`；
- 原来的 `now=lambda: NOW` 改为 `FixedClock(NOW)`；
- 不得修改这些测试的断言。

- [ ] **Step 4: 运行全部后端测试**

Run: `uv run python -m unittest discover -s tests`
Expected: 全部 PASS（基线 128 项 + Task 1 的 3 项 + 本任务 5 项）

- [ ] **Step 5: 更新 AGENTS.md 并提交**

```bash
git add backend tests AGENTS.md
git commit -m "refactor: move chan analysis pipeline into domain and inject clock"
```

---

### Task 3: 合并为单一组合入口

**Files:**
- Modify: `backend/app/container.py`
- Modify: `backend/app/main.py`
- Create: `tests/test_composition.py`

**Interfaces:**
- Consumes: Task 2 的 `SystemClock`、`MarketAnalysisPipeline`。
- Produces:
  - `backend/app/container.py`
    - `@dataclass(frozen=True) class Application: queries: QueryServices; commands: CommandServices; ranking_worker: RankingWorker | None`
    - `def build_application(settings: Settings) -> Application`：删除 `build_services` 与 `build_ranking_worker`。
  - 全进程只创建一个 `JsonHttpClient`、一条 `CachedMarketDataReader(FallbackMarketDataReader(...))` 行情链（新浪读取器带 `stock_search` 和 `SinaForwardAdjustment`，与原主链一致）、一个 `SqliteRankingStore`、一个 `SystemClock`。
  - `IndexClosedTradingDayReader` 使用这条共享行情链。
  - 当 `settings.ranking_enabled` 为 `False` 时，`ranking_worker` 为 `None`。
  - 这个函数只负责构造，不启动 worker。
  - `main.py`：模块加载时 `application = build_application(settings)`，再给 `app.state.queries` 和 `app.state.commands` 赋值；`lifespan` 里使用 `application.ranking_worker` 并负责启动和停止。为了避免与 FastAPI 实例名 `app` 冲突，变量名用 `services`。

- [ ] **Step 1: 写失败测试 `tests/test_composition.py`**

```python
def settings(tmp, enabled):
    return Settings(["*"], "http://127.0.0.1:9", "x" * 32, 60, False,
                    ranking_store_path=str(Path(tmp) / "r.sqlite3"), ranking_enabled=enabled)

def test_ranking_worker_shares_store_and_market_data_with_services(self):
    with tempfile.TemporaryDirectory() as tmp:
        built = build_application(settings(tmp, True))
        worker = built.ranking_worker
        self.assertIs(worker._reader, built.queries.get_rankings._reader)
        self.assertIs(worker._calendar._market_data, built.queries.get_market_snapshot._reader)
        self.assertIs(worker._command, built.commands.build_daily_rankings)

def test_disabled_ranking_builds_no_worker(self):
    with tempfile.TemporaryDirectory() as tmp:
        self.assertIsNone(build_application(settings(tmp, False)).ranking_worker)
```

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_composition -v`
Expected: ImportError，`build_application` 不存在

- [ ] **Step 3: 按 Interfaces 改写 `container.py` 与 `main.py`**

- [ ] **Step 4: 运行全部后端测试，并确认应用能启动**

Run: `uv run python -m unittest discover -s tests && CHANLUN_RANKING_ENABLED=false uv run python -c "import backend.app.main"`
Expected: 测试全部 PASS；导入无异常、无输出

- [ ] **Step 5: 提交**

```bash
git add backend/app tests/test_composition.py
git commit -m "refactor: single composition root shared by api and ranking worker"
```

---

### Task 4: 拆分榜单任务端口，任务状态移出领域层

**Files:**
- Create: `backend/application/ranking_jobs.py`
- Modify: `backend/domain/rankings.py`（删除 `RankingProgress`、`RankingView`、`RankingItem`）
- Modify: `backend/application/ports/writers.py`、`backend/application/ports/readers.py`
- Modify: `backend/application/commands/build_daily_rankings.py`、`backend/application/queries/get_rankings.py`
- Modify: `backend/infrastructure/persistence/sqlite_ranking_store.py`（只改 import）
- Modify: `backend/app/container.py`、`backend/app/ranking_worker.py`（只改 import 和构造调用）
- Modify（只改 import 和构造调用）：所有导入上述三个类或构造命令的测试。用 `grep -rln "RankingProgress\|RankingItem\|RankingView\|BuildDailyRankingsCommand(" tests` 列出。
- Modify: `tests/test_architecture.py`（追加两项）
- Modify: `AGENTS.md`（第二节 `application/` 下补 `ranking_jobs.py`；`writers.py` 的说明改为"报价采样、榜单租约、检查点、快照发布"）

**Interfaces:**
- Consumes: Task 2 的命令签名。
- Produces:
  - `backend/application/ranking_jobs.py`：`RankingProgress`、`RankingView`、`RankingItem` 三个 dataclass 从 `domain/rankings.py` 原样搬入，字段、默认值和 `frozen` 都不变。`domain/rankings.py` 只保留 `build_ranking_entry`、`rank_entries` 和它们的私有辅助函数。
  - `backend/application/ports/writers.py`：删除 `RankingWriter`，新增三个端口，都加 `@runtime_checkable`：
    - `class RankingLease(Protocol)`
      - `claim(trade_date, owner, now, lease_seconds, *, analysis_version) -> bool`
      - `heartbeat(owner, now, lease_seconds) -> bool`
      - `release(owner, now) -> None`
    - `class RankingCheckpointWriter(Protocol)`
      - `record(trade_date, item, progress, owner, now, lease_seconds) -> None`
      - `set_progress(progress, owner) -> None`
      - `fail(owner, message, now) -> None`
      - `note_error(message, now) -> None`
    - `class RankingPublisher(Protocol)`
      - `publish(board, owner, now) -> None`
    - 方法参数类型与原 `RankingWriter` 完全相同。
  - `backend/application/ports/readers.py`：删除没有调用方的 `StockPoolReader`；`RankingReader` 改为从 `..ranking_jobs` 导入类型。
  - 新的命令签名：`BuildDailyRankingsCommand.__init__(self, universe: StockUniverseReader, market_data: MarketDataReader, pipeline: MarketAnalysisPipeline, reader: RankingReader, lease: RankingLease, checkpoints: RankingCheckpointWriter, publisher: RankingPublisher, clock: Clock, *, workers=4, retries=2, lease_seconds=180)`。原来调用 `self._writer.X` 的地方，按上面的归属改成对应端口。
  - `container.py` 把同一个 `SqliteRankingStore` 实例同时传给 `reader`、`lease`、`checkpoints`、`publisher` 四个参数。
  - `SqliteRankingStore` 的方法、SQL 和 JSON 格式都不改。

- [ ] **Step 1: 在 `tests/test_architecture.py` 追加失败测试**

```python
def test_ranking_job_state_lives_in_application(self):
    import backend.domain.rankings as domain_rankings
    for name in ("RankingProgress", "RankingView", "RankingItem"):
        self.assertFalse(hasattr(domain_rankings, name), name)

def test_sqlite_store_implements_each_ranking_port(self):
    from backend.application.ports.writers import RankingCheckpointWriter, RankingLease, RankingPublisher
    with tempfile.TemporaryDirectory() as tmp:
        store = SqliteRankingStore(Path(tmp) / "r.sqlite3")
        for port in (RankingLease, RankingCheckpointWriter, RankingPublisher):
            self.assertIsInstance(store, port)
```

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_architecture -v`
Expected: 两项 FAIL / ImportError

- [ ] **Step 3: 按 Interfaces 搬移、拆分并更新所有调用方**

- [ ] **Step 4: 运行全部后端测试**

Run: `uv run python -m unittest discover -s tests`
Expected: 全部 PASS。`tests/test_analysis_versions.py` 的旧数据解码断言必须原样通过（Review Focus 3）。

- [ ] **Step 5: 更新 AGENTS.md 并提交**

```bash
git add backend tests AGENTS.md
git commit -m "refactor: split ranking job ports and move job state to application"
```

---

### Task 5: 前端网关显式映射榜单 DTO

**Files:**
- Modify: `frontend/src/infrastructure/api/marketGateway.js`
- Create: `frontend/src/infrastructure/api/marketGateway.test.js`

**Interfaces:**
- Produces（`marketGateway.js` 模块内函数，不导出）：
  - `toBuySellPoint(dto)`：返回 `null` 或白名单字段对象。字段为 `kind, side, pen_id, candle_index, time, price, confirmed, reason, invalidation_price, source_zone_id`；后两个缺失时为 `null`。
  - `toRankingEntry(dto)`：字段为
    - `rank, stock, bias, strength, latest, change_percent, invalidation_price`
    - `latest_point, buy_point, sell_point`：经 `toBuySellPoint` 映射
    - `trend`：映射为 `{ time, price }` 数组，缺失时为 `[]`
    - `signal_quality`：经现有 `toSignalQuality` 映射
    - `signal_group`：缺失时为 `'observation'`
    - `stock`：经现有 `toStock` 映射
  - `toRankingBoard(dto)`：字段为 `generated_at, timeframe, trade_date, total_count, analyzed_count, failed_count, state, progress_total, progress_processed, update_error, analysis_version, bullish, bearish`。其中 `bullish` 和 `bearish` 经 `toRankingEntry` 映射，缺失时为 `[]`；`analysis_version` 缺失时为 `1`。
  - `getRankings` 返回 `toRankingBoard(response)`；`toMarketSnapshot` 里的 `points` 改用 `toBuySellPoint`。
  - 字段名保持 snake_case，组件代码不改。

- [ ] **Step 1: 写失败测试**

`httpClient.js` 在模块加载时读取 `import.meta.env.BASE_URL`，所以测试文件先设 `process.env.BASE_URL = '/'`，再 `await import('./marketGateway.js')`；`globalThis.fetch` 替换为返回 `{ ok: true, json: async () => payload }` 的函数。

```js
test('getRankings drops fields the gateway does not map', async () => {
  const board = await gateway.getRankings(10)
  expect(board.bullish[0].internal_debug).toBeUndefined()
  expect(board.internal_note).toBeUndefined()
})

test('getRankings defaults fields missing from older backends', async () => {
  // payload 条目没有 signal_quality / signal_group / trend，board 没有 analysis_version
  const board = await gateway.getRankings(10)
  expect(board.bullish[0].signal_group).toBe('observation')
  expect(board.bullish[0].signal_quality).toBeNull()
  expect(board.bullish[0].trend).toEqual([])
  expect(board.analysis_version).toBe(1)
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && bun test src/infrastructure/api/marketGateway.test.js`
Expected: 第一项 FAIL（`internal_debug` 被透传），第二项中 `trend` 和 `analysis_version` 的断言 FAIL

- [ ] **Step 3: 按 Interfaces 实现映射函数**

- [ ] **Step 4: 运行前端测试与构建**

Run: `cd frontend && bun test src && bun run build`
Expected: 测试全部 PASS，构建成功

- [ ] **Step 5: 提交**

```bash
git add frontend/src/infrastructure/api
git commit -m "refactor: map ranking DTOs explicitly at the frontend gateway"
```

---

### Task 6: 端到端验证（AGENTS.md 第五节）

**Files:** 无代码改动。验证不通过时，回到对应任务修复。

- [ ] **Step 1: 全量测试与构建**

Run: `uv run python -m unittest discover -s tests && cd frontend && bun test src && bun run build`
Expected: 全部 PASS，构建成功

- [ ] **Step 2: 启动隔离后端**

用临时榜单库启动，避免写入 `var/rankings`：

Run: `CHANLUN_RANKING_STORE_PATH=$(mktemp -d)/r.sqlite3 CHANLUN_RANKING_ENABLED=false uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8001`

- [ ] **Step 3: 真实 HTTP 检查（另开终端，逐项记录状态码与关键字段）**

- `GET /api/stocks/search?q=600519` 和 `?q=510300`：股票和 ETF 都能搜到。
- `GET /api/chan?secid=1.600519&timeframe=X`：`X` 分别取 `1d`、`1h`、`30m`、`5m`，都返回 200；如果有买卖点，`signal_quality` 应非空。
- `GET /api/chan/confluence?secid=1.600519`：返回 4 个级别（Review Focus 5）。
- `GET /api/rankings?limit=5`：空库时 `state` 为 `pending`，返回 200。
- `GET /api/quote?secid=1.600519` 和 `POST /api/quote/samples`：报价与采样写入正常。采样会写到 `var/quotes`，测完删除本次新建的文件，或按 AGENTS.md 允许的方式检查。
- `GET /api/auth/session`：游客会话返回 200。`POST /api/auth/webview-login` 传无效 code 时返回错误响应，不能是 500。
- 带 `Origin: http://example.com` 头请求，确认响应带 CORS 头。
- `GET /api/chan?secid=bad`：返回 4xx，错误信息为中文。

- [ ] **Step 4: 汇报**

逐项列出结果，以及任何与重构前不一致的地方，然后停下等待用户确认。不推送。
