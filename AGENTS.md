# 项目架构规范

本项目前后端统一采用整洁架构。任何新增功能都必须遵守依赖方向、读写分离和边界转换规则，不得为了方便把框架、HTTP、数据库或第三方 SDK 依赖引入核心层。

## 一、依赖规则

依赖只能指向内层：`interfaces / presentation -> application -> domain`。`infrastructure` 实现 `application` 定义的端口，同样只能依赖内层。

- `domain`：不依赖 FastAPI、React、fetch、数据库、缓存、第三方数据源或其他外层。
- `application`：只依赖领域对象和端口，不导入具体框架或基础设施实现。
- `infrastructure`：负责 HTTP、缓存、数据库和第三方 SDK；实现 Reader/Writer/Gateway 端口。
- `interfaces / presentation`：负责 API DTO、路由、React 组件和交互状态，不承载核心业务规则。
- `app`：唯一组合根，负责创建适配器、用例并注入外层。其他模块不得自行实例化跨层具体实现。

跨层传递使用不可变领域对象或明确 DTO。第三方 JSON、数据库行和框架请求对象不得进入 `domain` 或 `application`。

## 二、后端目录与职责

```text
backend/
├── domain/
│   ├── models.py              # 不可变领域模型、周期和值校验
│   ├── errors.py              # 与 HTTP 状态码无关的领域异常
│   └── services/              # 缠论分析、MACD、背驰与买卖点、多级别联立、结构判断、分时序列合并等纯领域算法
├── application/
│   ├── ports/
│   │   ├── readers.py         # 查询端口（行情、报价、分时、搜索、榜单候选池）
│   │   └── writers.py         # 写入端口（报价采样）
│   ├── queries/               # 只读用例（含复用快照查询的榜单与多级别联立）
│   └── commands/              # 写用例（记录报价采样）
├── infrastructure/
│   ├── http/                  # 通用传输客户端
│   ├── market/                # 东方财富 K 线、实时报价、分时、搜索、候选池读取适配器；新浪备用 K 线与主备切换装饰器
│   ├── cache/                 # 缓存装饰器，不污染用例
│   └── persistence/           # 本地 JSON 报价存储，同时实现分时 Reader 与 Writer
├── interfaces/api/            # FastAPI 路由、Pydantic DTO、异常映射
└── app/                       # 组合根与应用启动（含 CORS 配置）
```

### 缠论买卖点约定

- 快照在笔、线段、笔中枢之外返回 `macd`（12/26/9，柱 = 2 ×（DIF − DEA））与 `signals`（走势类型、背驰、三类买卖点），由 `domain/services/chan_signals.py` 计算。
- 走势类型：最近两个笔中枢核心区间不重叠且依次抬高或降低为趋势，否则为盘整。
- 背驰：离开中枢的同向段创新高或新低，且其同号 MACD 柱面积小于进入段；只有趋势背驰产生一类买卖点，盘整背驰仅作提示。
- 二类：一类点后首次同向回抽不破一类点价格。三类：中枢后第一笔不再回到核心区间。末笔为候选时买卖点标记为未确认。
- `/api/chan/confluence` 比对日线、1 小时、30 分钟、5 分钟：次级别最近买卖点与大级别最近买卖点同向且在其后，视为印证。单个级别取数失败时跳过该级别。
- 买卖点是规则化的结构识别，界面必须保留"不构成买卖建议"的提示。

`backend/app/container.py` 返回 `QueryServices` 与 `CommandServices` 两组服务，路由分别从 `app.state.queries` 和 `app.state.commands` 取用。报价采样按 `var/quotes/{secid}-{YYYYMMDD}.json` 按日存储，写入时清理往日文件；`var/quotes/` 是运行时数据，不入库。

### 后端读写分离

- Query 只能读取，不得产生数据库写入、修改配置或其他业务副作用。
- 新增写入功能时建立 `application/commands` 和 `application/ports/writers.py`，由 Command 调用 Writer。
- Reader 与 Writer 分开定义，不创建一个同时承担所有读写的巨型 Repository。
- 当前唯一的业务写入是实时报价采样（`POST /api/quote/samples` → `RecordQuoteSampleCommand` → `QuoteWriter`）；不得为了形式完整创建空写库或无意义 Command。
- 缓存是读取适配器的装饰器；强制刷新只能绕过读取缓存，不得被当作业务写入。
- 新增数据源时实现对应端口，在 `backend/app/container.py` 注册；领域算法和 API 路由不应跟随修改。

## 三、前端目录与职责

```text
frontend/src/
├── domain/                    # 纯领域定义、选择器、交易时段和分时序列合并等业务规则
├── application/               # 股票搜索、快照与报价读取等查询用例，报价采样写入用例
├── infrastructure/api/        # fetch、HTTP 错误、DTO 转换和 Gateway
├── presentation/
│   ├── components/            # 渲染和用户交互
│   ├── hooks/                 # 请求生命周期、加载状态、防抖、URL 路由同步、实时轮询等交互状态
│   └── glossary.js            # 术语说明文案
├── app/container.js           # 前端唯一依赖装配位置
├── App.jsx                    # 展示层页面编排
└── main.jsx                   # 用例注入与 React 启动
```

### 前端读写分离

- React 组件和 Hooks 不得直接调用 `fetch`，必须调用 `application` 用例。
- 查询统一通过只读 Gateway 方法；写操作（当前为 `recordQuoteSample`）使用独立 Command 用例和写入 Gateway 方法，不得伪装成查询。未来新增收藏、配置保存等写操作同样处理。
- `infrastructure/api` 是 API DTO 边界，第三方字段不得散落到组件中。
- 展示组件可以负责本地 UI 状态，但行情统计和可复用业务判断应放入 `domain`，请求编排应放入 `application` 或展示 Hooks。
- 新增用例在 `frontend/src/app/container.js` 装配并注入，不允许组件直接实例化基础设施。

## 四、运行配置

- CORS 来源由环境变量 `CHANLUN_CORS_ORIGINS` 控制，多个来源用逗号分隔；未设置时为 `*`，允许任意客户端访问。不得在代码中写死主机或 IP。
- 开发时前端通过 Vite 代理 `/api` 到 `127.0.0.1:8000`，同源访问不依赖 CORS。

## 五、变更检查

修改架构或目录职责时必须同步更新本文件。完成变更后至少执行：

```bash
uv run python -m unittest discover -s tests
uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
cd frontend && bun run build
```

并通过真实 HTTP 请求验证股票与 ETF 搜索、四周期行情与买卖点、多级别联立、榜单、实时报价与采样写入、跨域响应头和错误响应；不得仅以导入成功或构建成功代替端到端验证。

## 六、分支与提交

- 除非用户特别说明，所有开发都直接在 `main` 分支上进行，不另建功能分支。
- 只在用户要求时提交或推送；提交前完成第五节的变更检查。
