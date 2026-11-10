# 证券标识去东方财富化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把系统统一的证券标识从东方财富格式 `1.600519` 换成与数据源无关的 `代码.交易所` 格式（如 `600519.SH`）。只有东方财富和新浪适配器知道各自的源格式；旧格式输入长期兼容。

**Architecture:** 领域层定义 `Exchange` 与 `SecurityId` 值对象，只认规范格式。应用层提供 `normalize_security_id`，把旧公开格式转成规范格式，所有用例在入口调用它。每个数据源适配器各自持有一个与规范格式互转的编解码模块。持久化层读旧数据时就地规范化，不重算、不改库结构。前端领域层镜像同一套规范化规则，页面路由遇到旧链接时自动改写为新格式。

**Tech Stack:** Python 3 + FastAPI + unittest（`uv run`）；React 19 + Vite，测试用 `bun test`。

**Spec:** 本文件"背景与决定"一节；项目规则见 `AGENTS.md`。

## 背景与决定

- 现状：`domain/models.py` 的 `validate_security_id` 用正则 `\d{1,3}\.[A-Za-z0-9.-]{1,24}` 校验东方财富的"市场号.代码"格式。这个格式贯穿 API 参数、前端 URL、小程序回跳参数、`var/quotes/{secid}-{YYYYMMDD}.json` 文件名和 SQLite 榜单快照。新浪适配器、前端 `marketHours.js` 和 `market.js` 都在自行拆解市场号。
- 用户决定（2026-11-10）：
  - 对外格式为 `代码.交易所`，交易所为大写 `SH`、`SZ`、`BJ`，例如 `600519.SH`、`000001.SZ`、`920118.BJ`；
  - 旧格式输入长期兼容，不设移除期限。
- 本计划另外做出的决定：
  - 本期只支持沪深北 A 股和境内 ETF（交易所 `SH`、`SZ`、`BJ`），港股和美股都去掉（用户 2026-11-10 确认）。原因：缠论买卖点连 A 股都还没回测验证，未验证的市场不应放在页面上。以后要加回来，只需在 `Exchange` 加成员、在适配器加映射。
  - 搜索结果不再返回港股和美股；旧格式 `116.`（港股）和 `105.`、`106.`、`107.`（美股）返回 400，提示"暂不支持该市场"。
  - 领域模型中的 `secid: str` 字段类型不变，只是取值改为规范字符串。这样持久化 JSON 结构不变，`ANALYSIS_VERSION` 也不需要升级。
  - 旧格式到规范格式的映射：`1.` → SH；`0.` 时代码以 `4`、`8`、`9` 开头为 BJ，否则为 SZ。这与现有 `sina_market_data.py:_sina_symbol` 的规则一致。
  - 报名系统和小程序不需要改：回跳参数允许的字符集 `[A-Za-z0-9._=&-]` 已经覆盖新格式，它们只是原样透传。报名系统 `openapi.yaml` 示例里写的是旧格式，属于另一个仓库，不在本计划内，完成后提醒用户即可。

## Global Constraints

- 在 `docs/superpowers/plans/2026-11-10-architecture-boundaries.md` 全部完成并经用户确认之后再开始。开始前工作区必须干净（`git status --short` 无输出）。
- 直接在 `main` 分支开发；只在用户明确授权后执行 Commit 步骤，未授权时每个任务验证完就停下汇报。
- 规范格式正则（前后端一致）：`^[0-9A-Z]{1,10}\.(SH|SZ|BJ)$`。输入允许小写和首尾空白，规范化时转成大写并去掉空白。
- 旧格式正则（前后端一致）：`^(0|1)\.[0-9A-Za-z]{1,10}$`。旧格式 `116.`、`105.`、`106.`、`107.` 开头的输入，错误文案为"暂不支持该市场"；其余非法输入沿用原文案"证券标识格式不正确"。
- 所有 API 输出的 `secid` 一律为规范格式，包括快照、报价、分时、联立、搜索、榜单。
- 不改 SQLite 表结构，不升级 `ANALYSIS_VERSION`；不删除、不改写 `var/` 下已有运行数据，读取时兼容旧格式即可。
- 东方财富和新浪的源格式（如 `1.600519`、`sh600519`）只能出现在 `backend/infrastructure/market/` 下。旧公开格式的解析只能出现在 `backend/application/security_ids.py` 和 `frontend/src/domain/security.js`。
- 不新增第三方依赖。
- 后端测试命令：`uv run python -m unittest discover -s tests`；前端：`cd frontend && bun test src && bun run build`。
- 已有测试大量使用 `1.600519` 之类的输入。断言输出 `secid` 的地方改成规范格式；输入可以保留旧格式，正好兼作兼容性测试。不得删除测试。

## Review Focus

1. **同代码不同交易所。** `000001` 既是上证指数（`1.000001`），也是平安银行（`0.000001`）。转换必须保留交易所，`IndexClosedTradingDayReader` 读取的必须是 `000001.SH`。→ Task 1 的 `test_same_code_on_two_exchanges_stays_distinct`，Task 2 的 `test_trading_calendar_reads_shanghai_index`。
2. **榜单补算中途升级。** 检查点里已存的是旧格式 secid，升级后续算时必须被识别为"已完成"，不能全部重算或产生重复条目。→ Task 4 的 `test_legacy_checkpoint_items_resume_under_canonical_ids`。
3. **当天的报价采样不能丢。** 部署当天，升级前写入的采样文件名是旧格式，升级后必须仍能读到，并在下次写入时合并进新文件。→ Task 4 的 `test_same_day_legacy_quote_file_is_read_and_merged`。
4. **旧分享链接要能打开。** 打开 `?secid=1.600519&timeframe=1d` 必须正常显示贵州茅台，地址栏改写为 `secid=600519.SH`；旧格式港股、美股链接回到默认证券，不白屏。→ Task 5 的 `readMarketRoute` 用例。
5. **小写输入和非法输入。** `600519.sh` 视为 `600519.SH`；`600519.XX`、`abc`、空串返回 400 和中文错误，不能出 500。→ Task 1 与 Task 3 的用例。

---

### Task 1: 领域值对象与应用层规范化

**Files:**
- Create: `backend/domain/security.py`
- Create: `backend/application/security_ids.py`
- Modify: `backend/domain/models.py:34-38`（删除 `validate_security_id` 和不再使用的 `re` 导入）
- Create: `tests/test_security_ids.py`

**Interfaces:**
- Produces:
  - `backend/domain/security.py`
    - `class Exchange(str, Enum)`：成员 `SH = "SH"`、`SZ = "SZ"`、`BJ = "BJ"`。
    - `@dataclass(frozen=True) class SecurityId`
      - 字段：`code: str`、`exchange: Exchange`
      - `__str__` 返回 `f"{code}.{exchange.value}"`
      - `@classmethod parse(cls, value: str) -> SecurityId`：只接受规范格式，否则抛 `InvalidSecurityId("证券标识格式不正确")`。
  - `backend/application/security_ids.py`
    - `def normalize_security_id(value: str) -> str`：接受规范格式或旧格式，返回规范字符串。旧港股和美股格式抛 `InvalidSecurityId("暂不支持该市场")`。
    - `def legacy_security_id(secid: str) -> str`：把规范格式转回旧公开格式。只供持久化层兼容读取旧文件使用。SH → `1.`，SZ 和 BJ → `0.`。
- 后续任务凡是原来调用 `validate_security_id` 的地方，都改用 `normalize_security_id`（Task 3）。

- [ ] **Step 1: 写失败测试 `tests/test_security_ids.py`**

```python
def test_canonical_ids_round_trip(self):
    for value in ("600519.SH", "000001.SZ", "920118.BJ"):
        self.assertEqual(str(SecurityId.parse(value)), value)

def test_legacy_ids_normalize_by_market_and_code_prefix(self):
    cases = {"1.600519": "600519.SH", "0.000001": "000001.SZ", "0.300750": "300750.SZ",
             "0.430047": "430047.BJ", "0.830799": "830799.BJ", "0.920118": "920118.BJ",
             "1.510300": "510300.SH", "0.159915": "159915.SZ"}
    for raw, expected in cases.items():
        self.assertEqual(normalize_security_id(raw), expected, raw)

def test_same_code_on_two_exchanges_stays_distinct(self):
    self.assertEqual(normalize_security_id("1.000001"), "000001.SH")
    self.assertEqual(normalize_security_id("0.000001"), "000001.SZ")

def test_lowercase_and_whitespace_are_normalized(self):
    self.assertEqual(normalize_security_id(" 600519.sh "), "600519.SH")

def test_hk_and_us_legacy_ids_are_rejected_as_unsupported_market(self):
    for raw in ("116.00700", "105.AAPL", "106.BABA", "107.SPY"):
        with self.assertRaisesRegex(InvalidSecurityId, "暂不支持该市场"):
            normalize_security_id(raw)

def test_malformed_ids_are_rejected(self):
    for raw in ("", "abc", "600519.XX", "600519", "2.600519", "1."):
        with self.assertRaisesRegex(InvalidSecurityId, "证券标识格式不正确"):
            normalize_security_id(raw)

def test_legacy_security_id_inverts_normalization(self):
    for raw in ("1.600519", "0.000001", "0.920118"):
        self.assertEqual(legacy_security_id(normalize_security_id(raw)), raw)
```

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_security_ids -v`
Expected: ImportError

- [ ] **Step 3: 按 Interfaces 实现两个模块，删除 `models.validate_security_id`**

删除后先不修调用方，它们由 Task 3 统一改。为了保证本任务结束时测试套件能跑，本任务内临时把各用例里的 `validate_security_id` 导入改为从 `backend.application.security_ids` 导入 `normalize_security_id` 并改名调用。这只是机械替换，Task 3 再补对应测试。

- [ ] **Step 4: 运行全部后端测试**

Run: `uv run python -m unittest discover -s tests`
Expected: 新增 7 项 PASS。原有测试中断言输出 secid 等于旧格式的用例会失败，这些按 Global Constraints 更新断言后全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend tests
git commit -m "feat: introduce exchange-qualified security ids with legacy normalization"
```

---

### Task 2: 数据源适配器自持编解码

**Files:**
- Create: `backend/infrastructure/market/eastmoney_codes.py`
- Modify: `backend/infrastructure/market/eastmoney_market_data.py`、`eastmoney_quote.py`、`eastmoney_intraday.py`、`eastmoney_stock_search.py`、`eastmoney_stock_pool.py`
- Modify: `backend/infrastructure/market/sina_market_data.py`（`_sina_symbol` 和 `_describe`）、`sina_stock_pool.py:70-80`
- Modify: `backend/infrastructure/market/closed_trading_day.py`（`"1.000001"` 改为 `"000001.SH"`）
- Modify: `tests/test_eastmoney_quote.py`、`tests/test_sina_stock_pool.py`、`tests/test_market_data_fallback.py`、`tests/test_rankings.py`（只改 secid 断言）
- Create: `tests/test_market_codecs.py`

**Interfaces:**
- Consumes: Task 1 的 `SecurityId`、`Exchange`。
- Produces:
  - `backend/infrastructure/market/eastmoney_codes.py`
    - `def to_eastmoney(secid: str) -> str`：规范格式 → `1.600519` 这类东方财富格式。
    - `def from_eastmoney(quote_id: str) -> str | None`：东方财富格式 → 规范格式。市场号 `116`（港股）、`105`、`106`、`107`（美股）和其他不支持的市场返回 `None`，调用方据此跳过。
    - 映射规则与 Task 1 的旧格式规则相同，但要在本模块独立实现。原因是这里是数据源格式，那边是公开兼容格式，将来可能各自变化。
  - 所有东方财富读取器：请求参数用 `to_eastmoney(secid)`；返回的 `MarketData.instrument.secid`、`RealtimeQuote.secid`、`QuoteSeries.secid`、`Stock.secid` 一律为规范格式。读取器的 `symbol` 取规范格式的代码部分，不再 `split`。
  - `EastmoneyStockSearchReader`：`SUPPORTED_CLASSES` 去掉 `"UsStock"` 和 `"HK"`；`from_eastmoney` 返回 `None` 的条目跳过。
  - `EastmoneyStockPoolReader`：`secid` 用 `from_eastmoney(f"{market}.{code}")` 生成。
  - `SinaMarketDataReader._sina_symbol(secid)`：解析为 `SecurityId`，SH、SZ、BJ 拼成 `f"{exchange.lower()}{code}"`。`_describe` 比对搜索结果时使用规范格式。
  - `SinaStockPoolReader`：`secid` 为 `f"{code}.{exchange.upper()}"`；`exchange` 仍然只接受 `sh`、`sz`。
  - 行情和分时读取器里按市场号切换时区与币种的分支（美股纽约时区、USD，港股 HKD）全部删除，时区固定为 `Asia/Shanghai`，币种固定为 `CNY`。

- [ ] **Step 1: 写失败测试 `tests/test_market_codecs.py`**

```python
def test_eastmoney_round_trip(self):
    for canonical, source in (("600519.SH", "1.600519"), ("000001.SZ", "0.000001"),
                              ("920118.BJ", "0.920118")):
        self.assertEqual(to_eastmoney(canonical), source)
        self.assertEqual(from_eastmoney(source), canonical)

def test_eastmoney_unsupported_markets_are_skipped(self):
    for source in ("116.00700", "105.AAPL"):
        self.assertIsNone(from_eastmoney(source))

def test_sina_symbol_from_canonical_id(self):
    reader = SinaMarketDataReader(StubHttpClient({}))
    self.assertEqual(reader._sina_symbol("600519.SH"), "sh600519")
    self.assertEqual(reader._sina_symbol("920118.BJ"), "bj920118")

def test_search_returns_canonical_ids_and_drops_hk_and_us(self):
    # StubHttpClient 返回 QuotationCodeTable.Data："1.600519"（AStock）、"116.00700"（HK）、"105.AAPL"（UsStock）各一条
    results = EastmoneyStockSearchReader(stub).search("a")
    self.assertEqual([s.secid for s in results], ["600519.SH"])

def test_trading_calendar_reads_shanghai_index(self):
    seen = []
    reader = SimpleNamespace(read=lambda secid, tf, **k: seen.append(secid) or market_data_with_day())
    IndexClosedTradingDayReader(reader).latest_on_or_before(date(2026, 11, 10))
    self.assertEqual(seen, ["000001.SH"])
```

`StubHttpClient` 复用 `tests/test_rankings.py` 中现有的同名桩。

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_market_codecs -v`
Expected: ImportError

- [ ] **Step 3: 按 Interfaces 修改所有适配器**

- [ ] **Step 4: 确认源格式没有泄漏出适配器目录**

Run: `grep -rnE "split\(\"\.\", 1\)|\"1\.000001\"|'1\.'|\"105\"" backend --include=*.py | grep -v "backend/infrastructure/market/"`
Expected: 无输出

- [ ] **Step 5: 运行全部后端测试并提交**

Run: `uv run python -m unittest discover -s tests`
Expected: 全部 PASS

```bash
git add backend tests
git commit -m "refactor: data source adapters translate their own security id formats"
```

---

### Task 3: 用例入口规范化与 API 契约

**Files:**
- Modify: `backend/application/queries/get_market_snapshot.py`、`get_quote.py`、`get_quote_series.py`、`get_signal_confluence.py`、`backend/application/commands/record_quote_sample.py`（确认都已使用 `normalize_security_id`）
- Modify: `backend/interfaces/api/routes.py`（`get_chan_snapshot` 的默认值 `secid="1.600519"` 改为 `"600519.SH"`）
- Modify: `AGENTS.md`（第二节补一条"证券标识约定"：规范格式、旧格式兼容规则、源格式只在适配器内）
- Create: `tests/test_security_id_api.py`

**Interfaces:**
- Consumes: Task 1 的 `normalize_security_id`、Task 2 的适配器。
- Produces: 所有接受 `secid` 的接口，输入接受两种格式，输出一律为规范格式。

- [ ] **Step 1: 写失败测试 `tests/test_security_id_api.py`**

用 `fastapi.testclient.TestClient` 包一个 FastAPI 实例：`include_router(router)`，调用 `register_error_handlers`，`app.state.queries` 注入假的 `QueryServices`。假的快照读取器记录收到的 secid，返回固定 `MarketData`；构造方式参考 `tests/test_ranking_api.py`。

```python
def test_legacy_and_canonical_inputs_reach_the_reader_as_canonical(self):
    for raw in ("1.600519", "600519.SH", "600519.sh"):
        response = client.get("/api/chan", params={"secid": raw, "timeframe": "1d"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["instrument"]["secid"], "600519.SH")
    self.assertEqual(set(reader.seen), {"600519.SH"})

def test_unsupported_and_malformed_ids_are_400_with_chinese_detail(self):
    for raw, detail in (("105.AAPL", "暂不支持该市场"), ("600519.XX", "证券标识格式不正确")):
        response = client.get("/api/chan", params={"secid": raw})
        self.assertEqual(response.status_code, 400)
        self.assertIn(detail, response.json()["detail"])

def test_quote_sample_is_written_under_canonical_id(self):
    response = client.post("/api/quote/samples", json={"secid": "1.600519", "time": "2026-11-10T02:00:00Z", "price": 1500})
    self.assertEqual(response.status_code, 200)
    self.assertEqual(writer.appended[0][0], "600519.SH")
```

如果 `InvalidSecurityId` 现有映射的状态码不是 400，以 `error_handlers.py` 的现有映射为准修改断言，不要改映射。

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_security_id_api -v`
Expected: 默认值和输出断言 FAIL（或其他未完成部分 FAIL）

- [ ] **Step 3: 补齐用例入口、修改路由默认值、更新 AGENTS.md**

- [ ] **Step 4: 运行全部后端测试并提交**

Run: `uv run python -m unittest discover -s tests`
Expected: 全部 PASS

```bash
git add backend tests AGENTS.md
git commit -m "feat: accept legacy security ids at use case boundaries and emit canonical ids"
```

---

### Task 4: 持久化旧数据兼容读取

**Files:**
- Modify: `backend/infrastructure/persistence/sqlite_ranking_store.py`（`_entry` 中的 `Stock` 和 `read_items` 中的 `RankingItem.secid` 经过规范化）
- Modify: `backend/infrastructure/persistence/json_quote_store.py`（读取时兼容旧文件名）
- Create: `tests/test_security_id_persistence.py`

**Interfaces:**
- Consumes: Task 1 的 `normalize_security_id`、`legacy_security_id`。
- Produces:
  - `SqliteRankingStore`：读取快照和检查点时，所有 `secid` 都经过 `normalize_security_id`；遇到无法规范化的旧条目（例如旧美股）时跳过该条目，不抛异常。写入沿用规范格式，表结构和 SQL 都不改。
  - `JsonQuoteStore`：当天的规范文件 `{secid}-{YYYYMMDD}.json` 不存在、而旧文件 `{legacy_security_id(secid)}-{YYYYMMDD}.json` 存在时，读取旧文件。追加写入时，把旧文件内容合并进规范文件并删除这个旧文件。返回的 `QuoteSeries.secid` 为规范格式。往日文件的清理规则不变。

- [ ] **Step 1: 写失败测试 `tests/test_security_id_persistence.py`**

```python
def test_legacy_snapshot_entries_decode_to_canonical_ids(self):
    # 用 store._connection() 直接写入一条 stock.secid="1.600519"、latest 等字段完整的旧快照 JSON
    board = store.read().board
    self.assertEqual(board.bullish[0].stock.secid, "600519.SH")

def test_undecodable_legacy_entries_are_skipped(self):
    # 旧快照含一条 "105.AAPL" 和一条 "1.600519"
    self.assertEqual([e.stock.secid for e in store.read().board.bullish], ["600519.SH"])

def test_legacy_checkpoint_items_resume_under_canonical_ids(self):
    # 直接 INSERT 一行 ranking_items：secid="1.600519"，payload 中 secid 同为旧格式，analysis_version=当前版本
    items = store.read_items(DAY, analysis_version=ANALYSIS_VERSION)
    self.assertEqual([item.secid for item in items], ["600519.SH"])

def test_same_day_legacy_quote_file_is_read_and_merged(self):
    # 在临时目录写入 "1.600519-20261110.json"，含一个采样点
    store = JsonQuoteStore(tmp)
    self.assertEqual(len(store.read_series("600519.SH").points), 1)
    store.append("600519.SH", later_time_same_day, 1501.0)
    self.assertEqual(len(store.read_series("600519.SH").points), 2)
    self.assertFalse((Path(tmp) / "1.600519-20261110.json").exists())
    self.assertTrue((Path(tmp) / "600519.SH-20261110.json").exists())
```

`JsonQuoteStore` 判断"今天"的方式以现有实现为准；如果它内部读取当前日期，测试里用它已有的注入方式或把采样时间设为当天，不要为此新增时钟依赖之外的改动。

- [ ] **Step 2: 运行确认失败**

Run: `uv run python -m unittest tests.test_security_id_persistence -v`
Expected: 4 项 FAIL

- [ ] **Step 3: 按 Interfaces 实现**

- [ ] **Step 4: 运行全部后端测试并提交**

Run: `uv run python -m unittest discover -s tests`
Expected: 全部 PASS，`tests/test_analysis_versions.py` 不改断言

```bash
git add backend/infrastructure/persistence tests/test_security_id_persistence.py
git commit -m "feat: read legacy security ids from persisted rankings and quote samples"
```

---

### Task 5: 前端规范化与旧链接改写

**Files:**
- Create: `frontend/src/domain/security.js`
- Create: `frontend/src/domain/security.test.js`
- Modify: `frontend/src/presentation/hooks/useMarketRoute.js`（删除 `SECID_PATTERN`，改用 `normalizeSecid`）
- Modify: `frontend/src/domain/marketHours.js`（只保留 A 股交易时段：删除 `getMarketSession` 的市场分支、港股和美股时段以及 `US_MARKETS`，导出函数签名不变）
- Modify: `frontend/src/domain/market.js`（`isStarMarket` 改用 `exchangeOf(secid) === 'SH'`；`DEFAULT_STOCK.secid` 改为 `'600519.SH'`，如果当前默认证券不是茅台，就换成该证券的规范格式）
- Modify: 断言了 secid 输出的现有前端测试（只改断言）
- Modify: `AGENTS.md` 第三节（`domain/` 说明补"证券标识规范化"）

**Interfaces:**
- Produces（`frontend/src/domain/security.js`）：
  - `export function normalizeSecid(value): string | null`：规则与后端 `normalize_security_id` 完全一致，包括两条正则和旧格式映射。非法或不支持的输入返回 `null`，不抛异常。
  - `export function exchangeOf(secid): 'SH' | 'SZ' | 'BJ' | null`
  - `export function codeOf(secid): string`
- `readMarketRoute(search)`：
  - `normalizeSecid` 返回 `null` 时回到 `DEFAULT_STOCK`，并且 `resolveStock: false`；
  - 否则 `stock.secid` 为规范格式，`symbol` 为 `codeOf(...)`。
  - `useMarketRoute` 现有的写回地址栏逻辑会自动把旧链接改写成新格式，无需另加代码。

- [ ] **Step 1: 写失败测试**

`frontend/src/domain/security.test.js`：

```js
test('normalizes legacy and canonical ids like the backend', () => {
  expect(normalizeSecid('1.600519')).toBe('600519.SH')
  expect(normalizeSecid('0.000001')).toBe('000001.SZ')
  expect(normalizeSecid('0.920118')).toBe('920118.BJ')
  expect(normalizeSecid(' 600519.sh ')).toBe('600519.SH')
  expect(normalizeSecid('116.00700')).toBeNull()
  expect(normalizeSecid('105.AAPL')).toBeNull()
  expect(normalizeSecid('600519.XX')).toBeNull()
})

test('exchange decides star market', () => {
  expect(isStarMarket({ secid: '688981.SH', symbol: '688981', market: '' })).toBe(true)
  expect(isStarMarket({ secid: '688981.SZ', symbol: '688981', market: '' })).toBe(false)
  // 2026-11-10 为周二：北京 10:00 开市、15:30 已收市
  expect(isMarketOpen('600519.SH', new Date('2026-11-10T02:00:00Z'))).toBe(true)
  expect(isMarketOpen('600519.SH', new Date('2026-11-10T07:30:00Z'))).toBe(false)
})

test('legacy route links resolve to canonical ids and unsupported ones fall back', () => {
  const legacy = readMarketRoute('?secid=1.600519&timeframe=1d')
  expect(legacy.stock.secid).toBe('600519.SH')
  expect(legacy.stock.symbol).toBe('600519')
  expect(legacy.resolveStock).toBe(true)
  const us = readMarketRoute('?secid=105.AAPL')
  expect(us.stock).toEqual(DEFAULT_STOCK)
  expect(us.resolveStock).toBe(false)
})
```

`readMarketRoute` 的测试放在现有测试它的文件中；如果没有，就放在 `security.test.js`，从 `../presentation/hooks/useMarketRoute.js` 导入。

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && bun test src/domain/security.test.js`
Expected: 模块不存在而失败

- [ ] **Step 3: 按 Interfaces 实现并修改调用方，更新 AGENTS.md 第三节**

- [ ] **Step 4: 确认前端没有自行拆解 secid**

Run: `grep -rnE "secid\.split|startsWith\('1\.'\)|US_MARKETS|SECID_PATTERN" frontend/src`
Expected: 无输出

- [ ] **Step 5: 运行前端测试与构建并提交**

Run: `cd frontend && bun test src && bun run build`
Expected: 全部 PASS，构建成功

```bash
git add frontend/src AGENTS.md
git commit -m "feat: frontend uses exchange-qualified security ids and rewrites legacy links"
```

---

### Task 6: 端到端验证

**Files:** 无代码改动。

- [ ] **Step 1: 全量测试与构建**

Run: `uv run python -m unittest discover -s tests && cd frontend && bun test src && bun run build`
Expected: 全部 PASS

- [ ] **Step 2: 用临时数据目录启动后端**

Run: `CHANLUN_RANKING_STORE_PATH=$(mktemp -d)/r.sqlite3 CHANLUN_RANKING_ENABLED=false uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8001`

- [ ] **Step 3: 真实 HTTP 检查，逐项记录**

- `GET /api/stocks/search?q=600519`、`?q=510300`、`?q=00700`、`?q=AAPL`：前两个返回的 `secid` 依次为 `600519.SH`、`510300.SH`；后两个结果中没有港股和美股。
- `GET /api/chan?secid=600519.SH&timeframe=X`：`X` 分别取 `1d`、`1h`、`30m`、`5m`。再用 `secid=1.600519` 请求一次，两种输入的响应 `instrument.secid` 都是 `600519.SH`。
- `GET /api/chan?secid=000001.SZ&timeframe=1d` 与 `?secid=000001.SH&timeframe=1d`：前者的名称是平安银行，后者是上证指数。
- 北交所任取一只（先用搜索找到）的日线快照返回 200；东方财富不可用时，新浪备用源对 `.BJ` 也可用。
- `GET /api/chan/confluence?secid=1.600519`：返回 4 个级别。
- `GET /api/quote?secid=600519.SH`，以及用旧格式 secid 的 `POST /api/quote/samples`：采样文件名为规范格式。测完删除本次在 `var/quotes` 新建的文件。
- `GET /api/chan?secid=116.00700`、`?secid=105.AAPL` 与 `?secid=600519.XX`：返回 400 和对应中文错误。
- 用前端开发服务打开 `/?secid=1.600519&timeframe=1d`：页面显示贵州茅台，地址栏变为 `secid=600519.SH`。在 390px 和 1440px 宽度下各截一张图。

- [ ] **Step 4: 汇报**

逐项列出结果。提醒用户：报名系统 `registration_system_go/docs/openapi.yaml` 的回跳示例仍写着 `secid=1.600519`，可按需更新。然后停下等待确认，不推送。
