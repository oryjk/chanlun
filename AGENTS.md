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
│   ├── tracking.py            # 资产清单与模拟跟踪纯领域模型与盈亏计算规则
│   ├── security.py            # 证券标识值对象 SecurityId 与交易所枚举 Exchange（SH/SZ/BJ）
│   ├── errors.py              # 与 HTTP 状态码无关的领域异常
│   └── services/              # 缠论分析、市场分析链路、MACD、背驰与买卖点、信号质量证据、多级别联立、结构判断、分时序列合并等纯领域算法
├── application/
│   ├── security_ids.py        # 证券标识规范化（兼容旧格式输入并转为规范格式）与持久化旧文件名映射
│   ├── ranking_jobs.py        # 榜单后台任务状态（进度、检查点条目、读取视图）
│   ├── ports/
│   │   ├── readers.py         # 查询端口（行情、单只/批量报价、市场资产清单、分时、搜索、全市场清单、收盘日、榜单快照、小程序外链）
│   │   ├── writers.py         # 写入端口（报价采样、榜单租约、检查点、快照发布）
│   │   ├── tracking.py        # 原生端用户跟踪持久化读写端口
│   │   ├── clock.py           # 时钟端口（带时区当前时间）
│   │   └── auth.py            # 身份兑换网关与会话编解码端口
│   ├── queries/               # 只读用例（行情、市场股票/ETF 清单、用户跟踪总览、复用快照查询的榜单与多级别联立、当前用户、小程序外链）
│   └── commands/              # 写用例（记录报价采样、一次性 code 登录、构建收盘榜单、开始/结束用户跟踪）
├── infrastructure/
│   ├── http/                  # 通用传输客户端
│   ├── market/                # 东方财富 K 线、实时报价、分时、搜索、候选池与资产清单适配器；新浪备用 K 线（前复权）、实时/批量报价、证券清单与资产清单，以及主备切换装饰器（主源连续失败时短时熔断）
│   ├── cache/                 # K 线内存缓存与 SQLite 全市场行情资产及最近 25 日收盘增量缓存（`asset_catalog_cache.py`，`var/cache/` 不入库），不污染用例
│   ├── persistence/           # 本地 JSON 报价存储、SQLite 榜单快照/租约/检查点、SQLite 用户跟踪存储（只读查询+独立写入）
│   └── auth/                  # 报名系统网关（code 兑换身份、URL Link）与 HMAC 签名会话
├── interfaces/api/            # FastAPI 路由（行情 routes.py、H5 登录 auth_routes.py、H5 跟踪 tracking_routes.py、原生登录 app_auth_routes.py、原生跟踪 app_tracking_routes.py）、Pydantic DTO、异常映射
└── app/                       # 组合根（container.py / tracking.py / app_auth.py）、环境配置 settings.py、应用启动与收盘榜单后台生命周期
```

### 证券标识约定

- 系统统一使用与数据源无关的规范格式 `代码.交易所`（正则 `^[0-9A-Z]{1,10}\.(SH|SZ|BJ)$`，例如 `600519.SH`、`000001.SZ`、`920118.BJ`）。领域层 `domain/security.py` 的 `SecurityId` 只接受规范格式，所有 API 输出的 `secid` 一律为规范格式。
- 用例入口统一通过 `application/security_ids.py` 的 `normalize_security_id` 规范化输入：去空白并转大写，同时长期兼容旧公开格式（`1.` 映射为 `SH`；`0.` 时代码以 `4`、`8`、`9` 开头映射为 `BJ`，其余映射为 `SZ`）。本期仅支持沪深北 A 股及境内 ETF，旧港股（`116.`）与美股（`105.`、`106.`、`107.`）输入返回 400「暂不支持该市场」，其余非法标识返回 400「证券标识格式不正确」。
- 东方财富（`1.600519`）和新浪（`sh600519`）等数据源专有格式只存在于 `infrastructure/market/` 适配器内部，不得泄漏到领域层、应用层、API 或前端。

### 登录与鉴权约定

- H5 身份来自报名系统（`../registration_system`）小程序，不在 H5 自建账号：小程序 `pages/chanlun/index` 落地页默认登录后，以 web-view 打开缠观并带上一次性 `webview_code`（60 秒、单次有效）。原生 App 使用独立的 Apple 身份映射，见第七节。
- 前端 `main.tsx` 渲染前取出并抹掉地址栏中的 `webview_code` 等桥接参数，交给 `POST /api/auth/webview-login`；后端用 code 调报名系统 `/auth/webview-codes/exchange` 换 JWT、再读 `/users/me`，然后签发缠观自己的 HttpOnly cookie `chanlun_session`（HMAC 签名、无状态、默认 30 天）。报名系统 JWT 不下发到前端。
- 不登录也能使用全部行情与榜单功能。跟踪、收藏等个人功能必须通过 `useAuth().requireLogin(action)` 调用：已登录直接执行，游客弹出登录引导。
- H5 模拟跟踪按登录渠道隔离持久化：小程序 `chanlun_session` Cookie 用户经 `/api/tracking*` 以 `h5:{user_id}` 命名空间读写；普通浏览器支持邮箱验证码注册与密码登录，经 `/api/app-auth/email/*` 获取 Bearer 会话并经 `/api/app-tracking*` 读写，与原生 Android 共用邮箱账户体系而与小程序 Cookie 会话完全隔离。
- 登录引导按环境区分：小程序 web-view 内通过 `wx.miniProgram.redirectTo` 回到落地页登录并自动返回原页面；微信内浏览器或普通浏览器支持邮箱登录注册及切换 `GET /api/auth/mini-program-link` 获取报名系统生成的 URL Link。回跳参数只保留 `secid`、`timeframe`、`view`，且只允许 `[A-Za-z0-9._=&-]`，与小程序和报名系统规则一致。
- URL Link 只能指向已发布小程序版本中存在的页面，新版小程序发布前该接口返回 502。

### 缠论买卖点约定

- 快照在笔、线段、笔中枢之外返回 `macd`（12/26/9，柱 = 2 ×（DIF − DEA））与 `signals`（走势类型、背驰、三类买卖点），由 `domain/services/chan_signals.py` 计算。
- 走势类型：最近两个笔中枢核心区间不重叠且依次抬高或降低为趋势，否则为盘整。
- 背驰：离开中枢的同向段创新高或新低，且其同号 MACD 柱面积小于进入段；只有趋势背驰产生一类买卖点，盘整背驰仅作提示。
- 二类：一类点后首次同向回抽不破一类点价格。三类：中枢后第一笔不再回到核心区间。末笔为候选时买卖点标记为未确认。
- `/api/chan/confluence` 比对日线、1 小时、30 分钟、5 分钟：两级别最近买卖点均已确认、同向且次级别结构端点不早于大级别，视为结构印证。候选不算已确认印证，端点先后不代表实际识别先后。单个级别取数失败时跳过该级别。
- `signal_quality` 是独立的最新信号质量描述，不改变原始买卖点。候选不能显示「可关注买入」；一类点以自身极值、二类点以一类点极值、三类点以关联中枢边界作为信号失效参考，不能用最新中枢替代历史信号的参考。
- 自结构端点起核对现有已完成 K 线，任一收盘严格越过固定参考即标记失效并记录首次越界日期、收盘价，价格恢复也不重新启用；盘中高低价触及另列事实，等于边界和未收盘越界不计。缺少可核对区间或旧快照未核查时只能列观察。MACD、量比仍是待验证证据，不启用未经回测的硬过滤或成功概率；参考越界事实不得冒充实盘识别/确认时间。
- 买卖点是规则化的结构识别，界面必须保留"不构成买卖建议"的提示。

`backend/app/container.py` 的 `build_application(settings)` 返回 `Application`（queries、commands、ranking_worker），路由分别从 `app.state.queries` 和 `app.state.commands` 取用。报价采样按 `var/quotes/{secid}-{YYYYMMDD}.json` 按日存储，写入时清理往日文件；`var/quotes/` 是运行时数据，不入库。

### 后端读写分离

- Query 只能读取，不得产生数据库写入、修改配置或其他业务副作用。
- 新增写入功能时建立 `application/commands` 和 `application/ports/writers.py`，由 Command 调用 Writer。
- Reader 与 Writer 分开定义，不创建一个同时承担所有读写的巨型 Repository。
- 业务写入包括实时报价采样（`RecordQuoteSampleCommand` → `QuoteWriter`）、消费一次性 code 的登录，以及后台构建收盘榜单（`BuildDailyRankingsCommand` → `RankingLease` / `RankingCheckpointWriter` / `RankingPublisher`）。不得为了形式完整创建空写库或无意义 Command。
- 缓存是读取适配器的装饰器；强制刷新只能绕过读取缓存，不得被当作业务写入。
- 新增数据源时实现对应端口，在 `backend/app/container.py` 注册；领域算法和 API 路由不应跟随修改。

### 全市场收盘榜单

- 覆盖沪深 A 股及境内上市 ETF，包含境内跨境 ETF。完整分页枚举、去重并核对数量，不截取成交额前 40 只；清单不完整不发布新结果。东方财富清单不可用时改用新浪行情中心沪 A、深 A、ETF 三个节点，逐节点核对数量。
- `GET /api/rankings` 只读取持久化快照和状态，不触发分析或写库；页面刷新和每分钟状态读取均不重算。
- `app/ranking_worker.py` 在应用 lifespan 中管理后台线程，默认北京时间 15:30 后构建最近已收盘交易日的日线榜单。实际交易日由指数日 K 确定，同交易日成功发布后不重复；启动时可补齐缺失收盘日，失败至少等待 15 分钟重试。
- 后台只使用指定收盘日及之前的 K 线，要求存在该日有效数据，并绕过盘中读取缓存；默认 4 线程、单只至多重试 2 次。SQLite 检查点支持恢复，进程停止时释放租约、下次启动立即续算，租约避免多进程重复更新；全部失败保留旧快照，部分失败明确展示覆盖数。
- `var/rankings/` 是运行时 SQLite 数据，不入库。查询使用只读连接，Command 通过 Writer 原子替换快照。页面区分首次准备和真正空榜，并展示收盘日期与分析覆盖数。
- 榜单由后端统一返回 `signal_group`：已确认、同向且端点起参考越界区间已核对、没有已知越界记录的结构优先，其余列入观察。`analysis_version` 区分规则版本；版本升级允许相同收盘日补算一次，检查点按版本隔离，保留旧快照直至新结果成功发布；GET 不触发版本重算。旧快照缺少核查字段时显示待更新，不伪装为已通过。
- 组内距离排序同样使用 `signal_quality.invalidation_price`，缺少信号自身参考时不得借用 `guidance` 的最新中枢参考分档。端点年龄由完整 K 线计算后续已收盘根数并核对端点索引与时间，不从抽样走势或自然日估算，也不等于距确认时间。
- 当前规则版本统一定义于 `domain/analysis_version.py`；运行时 `claim` / `read_items` 必须显式传版本，新建快照与进度默认当前版本。持久化旧数据缺版本字段时显式按旧版 1 解码，已记录的版本保持原值，不得随默认值升级。

### 分析方法与隔夜套利

- 榜单页提供「缠论 / 隔夜套利」方法入口，两种方法各用自己的查询与后台任务。新增方法沿用应用层依赖领域层的规则，由 `app` 装配适配器，并用 `GET /api/methods/<方法标识>` 暴露只读视图。
- 隔夜规则在 `domain/overnight.py`，命令和查询位于 `application/commands/overnight.py` 与 `application/queries/get_overnight.py`，数据源与存储实现位于 `infrastructure/market/overnight.py` 与 `infrastructure/persistence/sqlite_overnight.py`。`app/overnight.py` 负责装配，`app/overnight_worker.py` 由应用 lifespan 启停，每个存储仅允许一个后台任务持锁写入。
- 交易日取原始上证指数日 K。启动时按最近 20 个已完成市场交易日补齐沪深主板收盘封板事件，交易日 15:35 后尝试当日增量。封板池包含普通股和风险警示股，核对日期、分页总数与去重后原子写入。已核对日期通过 SQLite 读取，缺页或未收盘标为覆盖不足并重试。
- 尾盘仅在北京时间 14:30 至 15:00 前批量刷新。依次核对主板、涨幅 3%–5%、最近 20 个已完成交易日封板、量比 ≥ 1、换手率 5%–10%、总市值 50–200 亿元。报价需同日且最长 90 秒。先筛选后并发读取候选与沪深 300 的分时，按同一分钟相对昨收的涨幅比较，要求当日所有已结束分钟的收盘价均不低于分时均价。回踩观察要求该分钟有成交、低价向下接近均价（差距不超过 1 分钱且低价不低于均价）并收在均价上方。缺分钟或沪深 300 未对齐时不生成观察记录。
- 扫描轮次、回踩证据及候选和基准分时存入 `var/overnight/`，收盘后补全已跟踪的分时。每轮记录源覆盖、计算和取数耗时及最旧有效报价滞后。行情临时异常保留已记录的观察。`GET /api/methods/overnight` 只读 SQLite，不拉上游、不改写数据。
- 只在下一交易日 09:30 至 11:30 前展示回踩观察的清仓提醒。结合盘中最低价、当日涨停价及与前一交易日同一时刻的累计成交量，缩量且截至核对分钟维持一字涨停时提示复核。缺少或超过两分钟的例外证据时保留清仓提醒。网页和安卓仅在方法可见时轮询，界面保留「不构成买卖建议」。
- `CHANLUN_OVERNIGHT_STORE_PATH` 默认 `var/overnight/overnight.sqlite3`，`CHANLUN_OVERNIGHT_ENABLED` 默认为 `true`，隔离测试可关掉后台任务。

## 三、前端目录与职责

H5 前端全面采用严格模式 TypeScript（`.ts` / `.tsx`，`strict: true`、`target: ES2020`），`frontend/tsconfig.json` 与 `frontend/src/vite-env.d.ts` 定义编译边界；`frontend/dev/startup-diagnostics.js` 因在应用模块加载前由 Vite 原样注入捕获旧版 WebView 启动错误而保持普通 ES5 JS。

```text
frontend/src/
├── domain/                    # 纯领域类型（types.ts）、证券标识规范化、选择器、交易时段、分时合并与模拟跟踪规则
├── application/               # 端口契约（ports.ts）、市场资产/快照/报价/榜单/跟踪等只读查询用例，写命令用例与偏好用例
├── infrastructure/
│   ├── api/                   # fetch、HTTP 错误、DTO 转换和 Gateway（行情、跟踪、登录/邮箱认证）
│   ├── storage/               # localStorage 外观模式、邮箱 Bearer 令牌与跟踪偏好持久化
│   └── wechat/                # 微信 JS-SDK 桥接（web-view 内跳回小程序）
├── presentation/
│   ├── components/            # 无卡片极简分割线 TSX 组件与页面（行情四列表格、模拟跟踪、榜单、菜单与默认头像、AuthContext 与登录弹窗、K 线与分时图）
│   ├── hooks/                 # 请求生命周期、加载状态、防抖、URL 路由同步（market/detail/tracking/rankings/menu）、深浅主题与实时轮询状态
│   └── glossary.ts            # 术语说明文案
├── app/container.ts           # 前端唯一依赖装配位置
├── App.tsx                    # 展示层四栏导航与详情视图编排
└── main.tsx                   # 取出 web-view 桥接参数、识别运行环境、用例注入与 React 启动
```

### 前端读写分离

- React 组件和 Hooks 不得直接调用 `fetch`，必须调用 `application` 用例。
- 查询统一通过只读 Gateway 方法；写操作（`recordQuoteSample`、`startTracking`、`cancelTrackings`）使用独立 Command 用例和写入 Gateway 方法，不得伪装成查询。
- `infrastructure/api` 是 API DTO 边界，第三方字段不得散落到组件中。
- 展示组件可以负责本地 UI 状态，但行情统计和可复用业务判断应放入 `domain`，请求编排应放入 `application` 或展示 Hooks。
- 新增用例在 `frontend/src/app/container.ts` 装配并注入，不允许组件直接实例化基础设施。

### 前端视觉规范

界面对齐策略货架 Clarity 无卡片极简细分割线设计体系，保持国内专业交易分析工具的克制观感，同时兼容浅色与 OLED 纯黑深色模式（跟随系统 / 浅色 / 深色）。颜色与尺寸统一使用 `frontend/src/styles.css` 顶部变量（`:root` 与 `:root[data-theme="dark"]`）。

- 配色：浅色白色内容面 `--sheet` `#ffffff`，次级面 `--sunken` `#f5f6f8`，分割线 `--line` `#e3e6ea`；深色 OLED 纯黑 `--sheet` `#000000`，次级面 `--sunken` `#16181c`，分割线 `--line` `#2f3336`。墨蓝 `--accent`（浅色 `#2e4a6e` / 深色 `#5e89c0`）是唯一主色，用于选中、链接和主按钮。红 `--up` / 绿 `--down` 只表示涨跌与买卖点，零涨跌（`< 0.005%`）使用中性 `--text-secondary` 显示 `0.00%`（严禁显示红色 `+0.00%`）。图表语义色固定：金色 `--gold` 为笔，墨蓝为线段，紫色 `--violet` 为笔中枢，蓝灰为分型；ECharts 配置随 `resolvedTheme` 联动。
- 版式：一级导航固定为四栏「行情 / 跟踪 / 榜单 / 菜单」（手机 `< 768px` 固定底栏，桌面 `>= 768px` 融入顶栏）；全站统一使用无卡片纯色内容面与 `1px solid var(--line)` 细分割线，不使用漂浮卡片和投影。只有弹层（搜索结果、术语说明、登录引导、添加跟踪弹窗）使用 `--overlay-shadow`。「行情」页使用固定列宽语义化四列表格（桌面端含近20日走势列），保证 `标的 / 成交额 / 涨跌幅 / 最新价` 表头与单元格右侧零偏差对齐；「榜单」页顶部下划线切换「已确认同向 / 候选观察」，行内不展示冗余确认徽标并确保 4 字及以上证券名完整显示。
- 控件：圆角只用于小控件（`--radius` 4px），面板最多 6px；不使用胶囊形按钮或标签。周期切换用文字标签加短下划线；图层开关画出各自在图上的笔触作为图例。
- 层级靠字重、字号与留白表达：正文 13–15px，区块标题 15–16px 600，价格 700 并使用 `tabular-nums`；不用浅色底提示块、带底色的图标方块或"大数字 + 小标签"统计卡片。
- 文案：不用"A · B · C"中点拼接，并列信息用空格分隔或用逗号写成一句话；不用全大写标签。按钮写明动作，空状态和错误提示说明发生了什么、下一步怎么做。
- 榜单日期：顶部突出榜单收盘日期及完整更新时间（明确标注北京时间）；列表和图表买卖点日期标为结构端点日期，不等于当日已知信号。候选与已确认分组，首次识别和确认时间未追溯时直说未知；相对涨跌标注比较端点价，和行情截止日期分开表达。
- 榜单页的「缠论 / 隔夜套利」方法切换沿用文字与短下划线。隔夜套利用细分割线展示数据覆盖、延迟、候选规则证据及次日早盘提醒。
- 价格统一通过 `domain/market.ts` 的 `formatPrice` 格式化，位数由 `getPriceDecimals(stock)` 决定：股票 2 位，ETF（`market === 'ETF'`）3 位；不在组件中直接 `toFixed`。
- 图表上的文字标签（如笔中枢名称）需单独成层绘制在最上方，不得被 K 线和笔遮挡。
- 修改视觉规范时同步更新本节，并在手机（390px）与桌面（1440px）宽度下截图检查。

## 四、运行配置

- CORS 来源由环境变量 `CHANLUN_CORS_ORIGINS` 控制，多个来源用逗号分隔；未设置时为 `*`，允许任意客户端访问。不得在代码中写死主机或 IP。
- 开发时前端通过 Vite 代理 `/api` 到 `127.0.0.1:8000`，同源访问不依赖 CORS；会话 cookie 依赖同源部署。
- 微信开发者工具可能使用较旧的网页内核，读取数组末项使用下标，不依赖 `Array.prototype.at`；前端 Vite 开发服务会在应用模块前注入启动错误提示，白屏时直接显示错误与浏览器信息并隐藏 webview 桥接参数，正式构建不包含该提示。
- 生产部署在报名系统已配置的 web-view 业务域名下：`https://match.oryjk.cn/chanlun/`。前端以 `CHANLUN_BASE_PATH=/chanlun/ bun run build` 构建，API 请求自动带上该前缀，由 nginx 把 `/chanlun/api/` 转发到缠观后端。
- 登录相关环境变量：`CHANLUN_REGISTRATION_API_BASE`（报名系统 App API 根路径，默认 `http://127.0.0.1:18081/api/v1/app`）、`CHANLUN_SESSION_SECRET`（至少 32 字符；未配置时使用临时密钥，重启后登录态失效）、`CHANLUN_SESSION_MAX_AGE`（秒，默认 30 天）、`CHANLUN_SESSION_COOKIE_SECURE`（HTTPS 部署设为 `true`）。
- 榜单环境变量：`CHANLUN_RANKING_RUN_AT`（北京时间，默认 `15:30`，必须不早于 `15:00`）、`CHANLUN_RANKING_WORKERS`（默认 `4`，范围 1–16）、`CHANLUN_RANKING_STORE_PATH`（默认 `var/rankings/rankings.sqlite3`）、`CHANLUN_RANKING_ENABLED`（默认 `true`，隔离测试可设 `false`）。

## 五、变更检查

修改架构或目录职责时必须同步更新本文件；分层依赖由 `tests/test_architecture.py` 与 `frontend/src/architecture.test.ts` 自动检查。完成变更后至少执行：

```bash
uv run python -m unittest discover -s tests
uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
cd frontend && bun run typecheck && bun test src && bun run build
```

并通过真实 HTTP 请求验证股票与 ETF 搜索、四周期行情与买卖点、多级别联立、榜单、实时报价与采样写入、一次性 code 登录与游客会话、跨域响应头和错误响应；不得仅以导入成功或构建成功代替端到端验证。

## 六、分支与提交

- 除非用户特别说明，所有开发都直接在 `main` 分支上进行，不另建功能分支。
- 只在用户要求时提交或推送；提交前完成第五节的变更检查。

## 七、原生 iPhone 与 iPad

- `ios/ChanGuan/` 使用 SwiftUI 页面与 UIKit K 线图，最低 iOS/iPadOS 17，同一 target 支持 iPhone/iPad、横竖屏和窄分屏。`ios/README.md` 记录 Mac 构建与 TestFlight 接续步骤。
- 原生端同样遵循 `Presentation -> Application -> Domain`，`Infrastructure` 实现读写/身份端口，`App` 是唯一组合根。Domain/Application 只使用 Foundation，不导入 UIKit、SwiftUI、Security、AuthenticationServices；页面不创建具体网络或存储适配器。
- `Domain` 保存不可变行情/信号、格式化、时区、分时合并、视窗与坐标规则；`Application` 定义行情 Reader、采样 Writer、身份与本地会话端口；`Infrastructure` 负责明确 JSON DTO 边界、URLSession、Keychain、Apple 授权与页面选择持久化；`Presentation/Charts` 负责原生绘制、手势与 SwiftUI 桥接，不能读取账号或访问网络。
- 图表主图、量与 MACD 共用视窗，文字最后绘制；候选/确认、信号失效、端点日期与未知识别时间沿用 H5 口径。证券标识在客户端视为不透明字符串，不在图表与页面解析数据源市场号。
- 当前社交只是未来可能性，不创建社交 API、数据表或界面。图表仅通过分层渲染和坐标/选择事件保持可扩展。
- 原生登录使用 `POST /api/app-auth/challenge` 与 `POST /api/app-auth/apple`。服务端验证 Apple 签名、issuer、audience、过期时间及挑战 nonce 后兑换 code。挑战 5 分钟且原子消费，App 会话默认 30 天；客户端 Keychain 保存随机 Bearer，服务端只存摘要。H5 Cookie 与原生会话不混用。
- 原生账号模型位于 `domain/app_auth.py`，端口位于 `application/ports/app_auth.py`，Command 位于 `application/commands/app_login.py`，只读会话 Query 位于 `application/queries/app_session.py`。`infrastructure/auth/apple_gateway.py` 验证 Apple 凭证，`infrastructure/persistence/sqlite_app_auth.py` 实现独立读写端口，`interfaces/api/app_auth_routes.py` 负责 HTTP 边界，`app/app_auth.py` 是原生身份组合根并管理撤销重试生命周期。
- 原生内部用户使用 UUID，Apple issuer/subject 映射与报名系统整数 ID 隔离，不按邮箱自动合并，不实现微信绑定。删除账号先原子删除身份/全部会话并入队，再由后台撤销 Apple token；24 小时删除回执支持响应丢失后的幂等重试。Apple 通知验签、去重并核对事件时间，避免首次登录中的撤销被遗漏或旧通知删除新授权。
- Apple 登录默认关闭：`CHANLUN_APPLE_ENABLED=false`。启用需设置 `CHANLUN_APPLE_CLIENT_ID`（Bundle ID）、`CHANLUN_APPLE_TEAM_ID`、`CHANLUN_APPLE_KEY_ID`、`CHANLUN_APPLE_KEY_PATH`、至少 32 字符的稳定 `CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY`。账号库由 `CHANLUN_APP_AUTH_STORE_PATH` 控制，默认 `var/accounts/accounts.sqlite3`；私钥、`var/accounts/` 与 `ios/Local.xcconfig` 不入库。
- 原生端停止不可见页面和后台轮询，恢复前台重新判断市场时段和会话；请求与认证各自使用 generation，旧响应不得覆盖新证券或恢复已撤销的身份。报价按市场日期去重合并，避免跨日曲线混合。
- 当前机器没有 Swift/Xcode，用户明确允许先写源码并提交，随后在 Mac 编译。这里必须执行 `uv run --with tree-sitter --with tree-sitter-swift python ios/scripts/check_sources.py --syntax` 和后端/H5 检查；Swift 类型检查、Xcode Build、XCTest、模拟器/真机截图、签名和 TestFlight 安装必须在 Mac 完成，不能以静态语法检查冒充通过。

## 八、原生 Android（手机与平板）

- `android/` 使用 100% Kotlin（`2.0.21`）+ Jetpack Compose（`Compose BOM 2024.12.01`）+ 原生 `Canvas` K 线/分时图表，`compileSdk = 35`、`minSdk = 26`，同一应用自适应手机（底部导航 `行情 / 跟踪 / 榜单 / 菜单` 4 项、单列布局）与平板/横屏宽屏（左侧导航栏）。视觉严格对齐策略货架 X × TradingView 无卡片极简细分割线（`0.5dp`）风格与 OLED 纯黑深色模式（`backgroundCanvas = #000000`、`cardSurface = #000000`、`subtleSurface = #16181C`、`cardBorder = #2F3336`，`RobotoCondensed` `tnum` 等宽数字），支持系统 / 浅色 / 深色三种外观切换；「菜单」页内置 API 服务环境切换，默认使用 `开发环境（AI外网）`（`https://ai.oryjk.cn:82/chanlun-dev/`），并可选 `开发环境（233内网）`（`http://172.16.60.233:8000/`）与 `生产环境`（`https://match.oryjk.cn/chanlun/`），各环境登录会话按环境隔离持久化。`android/README.md` 记录构建与邮箱登录配置步骤。
- 行情与详情交互流程：**行情** Tab 为无卡片「股票 / ETF」双分类四列对齐实时列表（覆盖全市场沪深北 A 股与境内 ETF，支持 `offset/limit` 触底无感分页与真实总数展示，停牌股排在活跃股末尾，`标的 / 成交额 / 涨跌幅 / 最新价` 表头与单元格严格右对齐、支持排序、书签快捷跟踪；后端 SQLite 后台批量补齐并每日增量更新全部标的近 20 日收盘走势），点击任一标的或搜索结果在无卡片全屏上推抽屉 `AssetDetailSheetHost`（`AssetDetailScreen`）中打开多周期 K 线/分时与缠论结构详情并支持底部一键 `+ 跟踪 / 跟踪中`，返回时保持原列表滚动位置；**跟踪** Tab 对应策略货架模拟跟踪（每只 ¥10,000 模拟本金记账、6 档成长阶段徽章「萌芽 → 年轮」、汇总指标、置顶/排序/批量管理，`GET /api/app-tracking`、`POST /api/app-tracking/start`、`POST /api/app-tracking/cancel`）；**榜单** Tab 使用顶部下划线分段切换已确认同向与候选观察，列表行内不重复展示已确认/候选徽标且名称列保留足够宽度确保 4 字以内资产名完整展示不截断。
- 原生 Android 同样遵循 `Presentation -> Application -> Domain`，`Infrastructure` 实现读写/身份/环境/更新端口，`App`（`AppContainer.kt`）是唯一组合根。`domain` 与 `application` 不导入 `android.*`、`androidx.*`、`okhttp3.*` 或 `com.google.gson.*`，`presentation` 不导入 `infrastructure`，由 `android/app/src/test/java/cn/oryjk/changuan/ArchitectureTest.kt` 自动守护。
- Android 端登录采用 **账号密码登录 + 邮箱验证码注册**（游客可使用全部行情与榜单功能）：
  - `POST /api/app-auth/email/code`：校验未注册邮箱并发送 6 位数字验证码（10 分钟有效、60 秒发送冷却，最多校验 5 次）。
  - `POST /api/app-auth/email/register`：校验邮箱验证码并设置密码（`scrypt` 哈希存储），创建独立 UUID 账号并签发 30 天 Bearer 会话令牌。
  - `POST /api/app-auth/email/login`：使用邮箱与密码登录并签发 30 天 Bearer 会话令牌。
  - 会话恢复（`GET /api/app-auth/session`）、退出（`POST /api/app-auth/logout`）与删除账号（`POST /api/app-auth/delete-account`）与原生身份体系共用，`infrastructure/auth/smtp_gateway.py` 实现 `EmailCodeGateway` 端口。
- 邮箱认证环境变量：`backend/app/env.py` 在启动时加载根目录 `.env`，支持 `CHANLUN_EMAIL_AUTH_ENABLED`、`CHANLUN_SMTP_*` 与策略货架共用的飞书邮件键名回退（`SMTP_HOST`、`SMTP_PORT`、`SMTP_USERNAME`、`SMTP_PASSWORD`、`SMTP_FROM_EMAIL`、`SMTP_TLS_MODE`、`EMAIL_CODE_PEPPER`）以及 `CHANLUN_APP_AUTH_SECRET`。`.env`（`0600`）仅放服务器本机，严禁入库。
- **双通道 30 秒自动检测更新与 Shizuku / 系统安装**：客户端 `AppViewModel`（`UPDATE_CHECK_INTERVAL_MILLIS = 30_000L`）在前台每间隔 **30 秒**优先请求 `https://ai.oryjk.cn:82/chanlun-android/metadata.json` 并自动回退 `http://172.16.60.233/chanlun-android/metadata.json`（无缓存），发现远端 `versionCode > BuildConfig.VERSION_CODE` 时弹窗提示，下载器（`ApkDownloader`）支持主备 URL 回退与 SHA-256 校验，安装器校验同包名与签名后优先通过 Shizuku 或回退系统 `FileProvider` 完成安装；「菜单」页展示当前版本号、上次检测时间、「检测新版本」、「更新安装方式」与 `releases.json`「版本更新记录」。
- **每次修改完任务后的强制打包与 7T 硬盘发布流程**：
  1. 在 `android/app/build.gradle.kts` 中将 `versionCode` 递增 `+1` 并同步更新 `versionName`。
  2. 运行 `cd android && ./gradlew testDebugUnitTest assembleDebug` 确保单元测试（含 Robolectric 澄境截图校验）与打包通过且 0 Kotlin 警告。
  3. 执行 `git add -A && git commit -m "..." && git push origin main` 提交并推送到远端仓库。
  4. 执行 `./android/scripts/publish-apk.sh --skip-build --note "本次更新说明"` 将新包发布到 **7T 硬盘**（`/data7t/apk-downloads/chanlun-android`），保留最近 10 个版本安装包并更新 `releases.json`，自动校验双入口 `http://172.16.60.233/chanlun-android/` 与 `https://ai.oryjk.cn:82/chanlun-android/`（经 `out109` Nginx HTTPS 反向代理）。


