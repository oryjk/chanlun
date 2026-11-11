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
│   └── services/              # 缠论分析、市场分析链路、MACD、背驰与买卖点、信号质量证据、多级别联立、结构判断、分时序列合并等纯领域算法
├── application/
│   ├── ports/
│   │   ├── readers.py         # 查询端口（行情、报价、分时、搜索、全市场清单、收盘日、榜单快照、小程序外链）
│   │   ├── writers.py         # 写入端口（报价采样、榜单快照与检查点）
│   │   ├── clock.py           # 时钟端口（带时区当前时间）
│   │   └── auth.py            # 身份兑换网关与会话编解码端口
│   ├── queries/               # 只读用例（含复用快照查询的榜单与多级别联立、当前用户、小程序外链）
│   └── commands/              # 写用例（记录报价采样、一次性 code 登录、构建收盘榜单）
├── infrastructure/
│   ├── http/                  # 通用传输客户端
│   ├── market/                # 东方财富 K 线、实时报价、分时、搜索、候选池读取适配器；新浪备用 K 线（按新浪前复权因子换算，与东方财富口径一致）与证券清单，以及主备切换装饰器（主源连续失败时短时熔断）
│   ├── cache/                 # 缓存装饰器，不污染用例
│   ├── persistence/           # 本地 JSON 报价存储、SQLite 榜单快照/租约/检查点，各自实现独立 Reader/Writer
│   └── auth/                  # 报名系统网关（code 兑换身份、URL Link）与 HMAC 签名会话
├── interfaces/api/            # FastAPI 路由（行情 routes.py、登录 auth_routes.py）、Pydantic DTO、异常映射
└── app/                       # 组合根、环境配置 settings.py、应用启动与收盘榜单后台生命周期
```

### 登录与鉴权约定

- H5 身份来自报名系统（`../registration_system`）小程序，不在 H5 自建账号：小程序 `pages/chanlun/index` 落地页默认登录后，以 web-view 打开缠观并带上一次性 `webview_code`（60 秒、单次有效）。原生 App 使用独立的 Apple 身份映射，见第七节。
- 前端 `main.jsx` 渲染前取出并抹掉地址栏中的 `webview_code` 等桥接参数，交给 `POST /api/auth/webview-login`；后端用 code 调报名系统 `/auth/webview-codes/exchange` 换 JWT、再读 `/users/me`，然后签发缠观自己的 HttpOnly cookie `chanlun_session`（HMAC 签名、无状态、默认 30 天）。报名系统 JWT 不下发到前端。
- 不登录也能使用全部行情功能。收藏、跟踪等个人功能必须通过 `useAuth().requireLogin(action)` 调用：已登录直接执行，游客弹出登录引导。
- 登录引导按环境区分：小程序 web-view 内通过 `wx.miniProgram.redirectTo` 回到落地页登录并自动返回原页面；微信内浏览器或普通浏览器调 `GET /api/auth/mini-program-link` 获取报名系统生成的 URL Link。回跳参数只保留 `secid`、`timeframe`、`view`，且只允许 `[A-Za-z0-9._=&-]`，与小程序和报名系统规则一致。
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

`backend/app/container.py` 返回 `QueryServices` 与 `CommandServices` 两组服务，路由分别从 `app.state.queries` 和 `app.state.commands` 取用。报价采样按 `var/quotes/{secid}-{YYYYMMDD}.json` 按日存储，写入时清理往日文件；`var/quotes/` 是运行时数据，不入库。

### 后端读写分离

- Query 只能读取，不得产生数据库写入、修改配置或其他业务副作用。
- 新增写入功能时建立 `application/commands` 和 `application/ports/writers.py`，由 Command 调用 Writer。
- Reader 与 Writer 分开定义，不创建一个同时承担所有读写的巨型 Repository。
- 业务写入包括实时报价采样（`RecordQuoteSampleCommand` → `QuoteWriter`）、消费一次性 code 的登录，以及后台构建收盘榜单（`BuildDailyRankingsCommand` → `RankingWriter`）。不得为了形式完整创建空写库或无意义 Command。
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

## 三、前端目录与职责

```text
frontend/src/
├── domain/                    # 纯领域定义、选择器、交易时段和分时序列合并等业务规则
├── application/               # 股票搜索、快照与报价读取等查询用例，报价采样写入用例，会话查询、code 登录与登录引导用例
├── infrastructure/
│   ├── api/                   # fetch、HTTP 错误、DTO 转换和 Gateway（行情、登录）
│   └── wechat/                # 微信 JS-SDK 桥接（web-view 内跳回小程序）
├── presentation/
│   ├── components/            # 渲染和用户交互（含 AuthContext 登录状态、账号标识与登录引导弹窗）
│   ├── hooks/                 # 请求生命周期、加载状态、防抖、URL 路由同步、实时轮询等交互状态
│   └── glossary.js            # 术语说明文案
├── app/container.js           # 前端唯一依赖装配位置
├── App.jsx                    # 展示层页面编排
└── main.jsx                   # 取出 web-view 桥接参数、识别运行环境、用例注入与 React 启动
```

### 前端读写分离

- React 组件和 Hooks 不得直接调用 `fetch`，必须调用 `application` 用例。
- 查询统一通过只读 Gateway 方法；写操作（当前为 `recordQuoteSample`）使用独立 Command 用例和写入 Gateway 方法，不得伪装成查询。未来新增收藏、配置保存等写操作同样处理。
- `infrastructure/api` 是 API DTO 边界，第三方字段不得散落到组件中。
- 展示组件可以负责本地 UI 状态，但行情统计和可复用业务判断应放入 `domain`，请求编排应放入 `application` 或展示 Hooks。
- 新增用例在 `frontend/src/app/container.js` 装配并注入，不允许组件直接实例化基础设施。

### 前端视觉规范

界面按国内行情软件的习惯设计，是给交易者用的分析工具，不做营销页式的装饰。颜色与尺寸统一使用 `frontend/src/styles.css` 顶部的变量，新增界面不得另起一套配色。

- 配色：白色内容面 `--sheet`，冷灰分隔色 `--page` `#eef0f2`；墨蓝 `--accent` `#2e4a6e` 是唯一主色，用于选中、链接和主按钮。红 `--up` / 绿 `--down` 只表示涨跌与买卖点，不得用作主色或装饰色。图表语义色固定：金色 `--gold` 为笔，墨蓝为线段，紫色 `--violet` 为笔中枢，蓝灰为分型。ECharts 配置中的颜色与上述变量保持一致。
- 版式：手机端整页白底、内容通栏，区块之间用 8px `--page` 色带分隔，列表行用 1px `--line` 细线；不使用漂浮卡片和投影。桌面端为带 1px 边框的白色面板，同样不加投影。只有弹层（搜索结果、术语说明、登录引导）使用 `--overlay-shadow`。
- 控件：圆角只用于小控件（`--radius` 4px），面板最多 6px；不使用胶囊形按钮或标签。周期切换用文字标签加短下划线；图层开关画出各自在图上的笔触作为图例。
- 层级靠字重、字号与留白表达：正文 13–15px，区块标题 15–16px 600，价格 700 并使用 `tabular-nums`；不用浅色底提示块、带底色的图标方块或"大数字 + 小标签"统计卡片。
- 文案：不用"A · B · C"中点拼接，并列信息用空格分隔或用逗号写成一句话；不用全大写标签。按钮写明动作，空状态和错误提示说明发生了什么、下一步怎么做。
- 榜单日期：顶部突出榜单收盘日期及完整更新时间（明确标注北京时间）；列表和图表买卖点日期标为结构端点日期，不等于当日已知信号。候选与已确认分组，首次识别和确认时间未追溯时直说未知；相对涨跌标注比较端点价，和行情截止日期分开表达。
- 价格统一通过 `domain/market.js` 的 `formatPrice` 格式化，位数由 `getPriceDecimals(stock)` 决定：股票 2 位，ETF（`market === 'ETF'`）3 位；不在组件中直接 `toFixed`。
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

修改架构或目录职责时必须同步更新本文件；分层依赖由 `tests/test_architecture.py` 与 `frontend/src/architecture.test.js` 自动检查。完成变更后至少执行：

```bash
uv run python -m unittest discover -s tests
uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
cd frontend && bun test src && bun run build
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
