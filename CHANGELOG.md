# 变更日志

## 2026-11-17 · 1.2.4（versionCode 9）· 修复高分屏 K 线买卖点与中枢图标只显示一半的问题

- 变更（图表密度缩放与基线居中修复）：
  - 在 [ChartGeometry.kt](android/app/src/main/java/cn/oryjk/changuan/domain/ChartGeometry.kt) 新增纯 Kotlin 几何辅助 `ChartBadgeBox`、`computePointBadgeBox` 与 `computeLabelPillBox`，严格基于 `Paint.FontMetrics`（`ascent` / `descent`）、垂直间距与内边距统一计算图标包围盒和文字居中基线。
  - 修复 [MarketChartCanvas.kt](android/app/src/main/java/cn/oryjk/changuan/presentation/charts/MarketChartCanvas.kt) 与 [RealtimeChartCanvas.kt](android/app/src/main/java/cn/oryjk/changuan/presentation/charts/RealtimeChartCanvas.kt) 中混用 `10f * density` 文字像素与硬编码裸像素框高（如 `boxH = 16f`、`boxTop + 12f`）导致 2x–3.5x 高分屏手机上确认买卖点（`B1` / `B2` / `S3`）、候选虚线框（`B3?`）及中枢徽标（`Z1` / `Z2` / `Z3?`）被裁切仅显示下半部分的问题；全量绘图线宽、虚线间隔、分型偏移、日期留白及图标间隙统一改用 `.dp.toPx()`，并在主图上下预留徽标安全净空且在深浅模式下均以高对比白色绘制已确认徽标文字。
  - 在 [ChartGeometryTest.kt](android/app/src/test/java/cn/oryjk/changuan/ChartGeometryTest.kt) 与 [ClarityUiScreenshotTest.kt](android/app/src/test/java/cn/oryjk/changuan/ClarityUiScreenshotTest.kt) 增加 `1x`–`3.5x` 边界与基线单测及 `3.0x`（`w390dp-h844dp-xxhdpi`）像素级高分屏实图检验，并将 Android 版本升级至 `1.2.4`（`versionCode = 9`）。

## 2026-11-17 · H5 前端全面迁移至严格模式 TypeScript（.ts / .tsx）

- 变更（工程化配置与全局类型边界）：
  - 新增 [frontend/tsconfig.json](frontend/tsconfig.json)（开启 `strict: true`、锁定 `target: ES2020` 在编译期静态禁止 `Array.prototype.at` 等旧版 WebView 不兼容语法，显式声明 `"types": ["bun", "node"]`）与 [frontend/src/vite-env.d.ts](frontend/src/vite-env.d.ts)（声明 Vite 环境变量及微信 JS-SDK 全局接口）；安装 `typescript`、`@types/react`、`@types/react-dom`、`@types/bun`。
  - 在 [frontend/package.json](frontend/package.json) 新增 `typecheck` 脚本（`tsc --noEmit`）并将 `build` 升级为 `tsc --noEmit && vite build`；配置与入口同步迁移至 [frontend/vite.config.ts](frontend/vite.config.ts) 与 [frontend/index.html](frontend/index.html)（`/src/main.tsx`），保留 `frontend/dev/startup-diagnostics.js` 作为开发态普通 ES5 前置脚本。
- 变更（整洁架构全量 `.ts` / `.tsx` 迁移与纯 TS 守卫）：
  - `domain/`：新建 [frontend/src/domain/types.ts](frontend/src/domain/types.ts) 沉淀不可变领域接口与联合类型，全量迁移 `auth.ts`、`security.ts`、`timeframes.ts`、`marketHours.ts`、`quoteSeries.ts`、`market.ts`、`signals.ts`、`rankings.ts`、`tracking.ts` 及其单测。
  - `application/`：新建 [frontend/src/application/ports.ts](frontend/src/application/ports.ts) 定义 `MarketGateway`、`AuthGateway`、`TrackingGateway`、`MiniProgramBridge`、`PreferencesStorage` 与 `AppUseCases` 端口契约，全量迁移 16 个读写用例。
  - `infrastructure/` 与 `app/`：迁移 `httpClient.ts`、`marketGateway.ts`、`authGateway.ts`、`trackingGateway.ts`、`localPreferences.ts`、`miniProgramBridge.ts` 及唯一组合根 [frontend/src/app/container.ts](frontend/src/app/container.ts)。
  - `presentation/`、`App.tsx` 与 `main.tsx`：迁移 `glossary.ts`、全部 10 个 Hooks、全部 19 个 TSX 组件（含 ECharts 严格类型选项与主题配色），并移除 `frontend/src/` 下全部历史 `.js`/`.jsx` 文件。
  - [frontend/src/architecture.test.ts](frontend/src/architecture.test.ts) 与 [AGENTS.md](AGENTS.md) 同步更新，新增 `src uses TypeScript only` 自动化检查禁止 `.js`/`.jsx` 回流到源码目录。
- 验证：完成 `bun run typecheck` 零错误、`bun test src`（58 项全通过）、根目录及 `/chanlun/` 双构建、`Playwright` 320px/375px/390px/600px/768px/1440px 与四列表对齐/模拟跟踪 E2E 验证，以及后端单元测试与 HTTP 冒烟验证。

## 2026-11-17 · 1.2.3（versionCode 8）· 全市场行情分页覆盖与 SQLite 20 日走势全量补齐/每日增量更新

- 变更（后端全市场清单分页与 20 日走势落库增量同步）：
  - `MarketAssetCatalogAdapter` 完整分页枚举沪 A（`sh_a`）、深 A（`sz_a`）、京 A（`hs_bjs` 北交所）及境内 ETF（`etf_hq_fund`），保留停牌股并在全市场排序中统一垫底；`EastmoneyStockSearchReader` 同步支持北交所（`NEEQ / 京A`）搜索与名称解析。
  - `GET /api/market/assets` 新增 `offset`（默认 `0`）与 `limit`（默认 `100`、上限 `500`）分页参数，响应新增 `total`、`offset`、`has_more`，并结合内存快照缓存实现全市场毫秒级分页与排序。
  - `SqliteAssetCatalogCache` 新增 `asset_daily_bars` 与 `asset_sparkline_sync` 表；应用启动后台线程 `asset-catalog-sync` 按每批 100 只、12 线程并发补齐全市场股票与 ETF 近 20 个交易日收盘走势落库，支持盘中实时拼接、除权跳空比例缩放并触发前复权全量覆盖，且在收盘后（`15:05+`）直接从全市场行情快照零单股 K 线请求完成每日增量更新，多日停机仅按缺口天数拉取补齐。
- 变更（Android 原生端全市场真实总数与触底无感分页）：
  - `MarketGateway`、`AppViewModel` 与 `MarketScreen` 对接 `offset / limit / total / has_more`：表头展示全市场真实总数（沪深北 5900+ 只股票、境内 1600+ 只 ETF），首屏加载 100 条，列表触底自动无感续接下一页并按 `secid` 去重合并，切换分类/排序回到首行，打开详情抽屉返回保持原滚动位置；版本递增至 `1.2.3`（`versionCode = 8`）。
- 验证：完成 backend 203 项单测、iOS Swift 语法校验、`./gradlew testDebugUnitTest assembleDebug`（0 Kotlin 警告）及 `:8000` 实盘全市场 SQLite 落库与接口验证。

## 2026-11-17 · H5 对齐 Clarity 无卡片风格、四列对齐行情列表、模拟跟踪、榜单与邮箱登录

- 变更（后端 H5 跟踪路由与隔离）：
  - 新增 `backend/interfaces/api/tracking_routes.py`（`GET /api/tracking`、`POST /api/tracking/start`、`POST /api/tracking/cancel`），基于 `chanlun_session` HttpOnly Cookie 校验小程序登录用户并以 `h5:{user_id}` 命名空间复用 `NativeTrackingServices`，并在 `tests/test_market_assets_and_tracking.py` 补全隔离与鉴权 HTTP 测试。
- 变更（H5 四栏导航与无卡片视觉重塑）：
  - 一级导航重构为 `行情 / 跟踪 / 榜单 / 菜单` 四项（手机端固定底栏，桌面端融入顶栏），全站去除卡片套框与阴影，改用 `1px` 细分割线并新增 `:root[data-theme="dark"]` OLED 纯黑深色主题及 ECharts 深浅联动配色。
  - **行情页**：改版为无卡片「股票 / ETF」实时行情列表，采用固定列宽语义化表头实现 `标的 / 成交额 / 涨跌幅 / 最新价`（桌面端含近20日走势）右侧像素级对齐，支持升/降序排序、书签快捷跟踪、零值中性 `0.00%`，点击进入多周期 K 线/分时结构详情页（支持 `?secid=...` 深链与 `← 返回`）。
  - **跟踪页**：新增第 2 栏「跟踪」，复用 `¥10,000` 模拟本金记账、六档植物成长阶段图标与徽章（萌芽→年轮）、盈亏汇总条、`+ 跟踪` 搜索添加弹窗、排序/置顶/上下移动与批量结束管理。
  - **榜单页**：采用顶部下划线 Tab 切换「已确认同向 / 候选观察」与「偏多 / 偏空」+ 板块筛选，移除行内冗余确认徽标并保证 4 字及以上证券名完整展示不截断。
  - **菜单与邮箱认证**：新增「菜单」页（默认几何头像、跟随系统/浅色/深色 OLED 切换、跟踪角标入口、缠论术语解释）及邮箱验证码注册/密码登录窗口（浏览器环境默认展示并支持切换小程序链接，Bearer 会话与 Android 账号数据互通）。
- 验证：完成 backend 200 项、frontend `bun test src` 57 项、`vite build` 根目录与 `/chanlun/` 双构建，以及 Playwright 320px/375px/390px/600px/768px/1440px 图表稳定、榜单规则、四列表头零偏差对齐及浅深色实机截图检查。

## 2026-11-17 · 1.2.2（versionCode 7）· 行情列表改为四列严格对齐布局（标的 / 成交额 / 涨跌幅 / 最新价）

- 变更（四列对齐表格布局）：重构 [MarketScreen.kt](android/app/src/main/java/cn/oryjk/changuan/presentation/screens/MarketScreen.kt) 列表与表头，使表头与数据行共享同一列宽与右侧对齐基线，「成交额」「涨跌幅」「最新价」表头正下方分别直接展示 `57.67 亿`、`+1.30%`、`1453.90`（停牌无成交展示 `停牌`），左列仅保留资产名称、书签、代码市场与买卖点标签，解决此前成交额与涨跌幅下方未对齐的问题，并在 Robolectric 测试中添加表头与单元格像素坐标边界断言。

## 2026-11-17 · 1.2.1（versionCode 6）· 修复行情列表成交额/涨跌幅为空与盘前清零问题并补齐走势小线

- 变更（后端沪深分节点查询、盘前缓存与 20 日走势小线补齐）：
  - `MarketAssetCatalogAdapter` 查询股票改为分别请求新浪 `sh_a` 与 `sz_a` 并过滤停牌/零成交标的多页补齐，消除旧 `hs_a` 节点中 88 只北交所代码被过滤后升序仅剩 12 只以及停牌 `*ST` / 零成交 ETF 占榜首的问题。
  - 新增 `SqliteAssetCatalogCache`（`var/cache/asset_catalog.sqlite3`）按交易日持久化活跃资产快照，并结合 `hq.sinajs.cn` 实时探针判断盘前 `09:00–09:25` 交易所清零窗口；清零期自动服务上一交易日完整成交额与涨跌幅（冷启动期间自动通过日 K 线补齐）。
  - 返回 `as_of_date` 取自真实行情日期，不再被旧收盘榜单日期覆盖；同时通过新浪 JSONP 日 K 线（腾讯 `fqkline` 与东方财富 `push2his` 备用）并发补齐列表全量 60 只标的的 20 日走势小线，并在 `JsonHttpClient` 中默认直连国内行情源避免环境变量代理干扰。
- 变更（Android 客户端列表与涨跌幅展示修复）：
  - 修复 `MarketScreen.kt` `MarketAssetLedgerRow` 中 `signalLabel` 与 `成交额` 的 `else if` 互斥分支 bug，统一同时展示 `代码 市场  成交 XX.XX 亿  买卖点`（停牌零成交显式标注 `停牌/未成交`）。
  - 修正 `MarketFormat.percent` / `signedPrice` 在零值（`< 0.005%`）时误加 `+` 号被渲染成红色 `+0.00%` 的问题，零涨跌保持中性灰 `0.00%`。
  - Android 版本升级为 `1.2.1`（`versionCode = 6`）。

## 2026-11-17 · 1.2.0（versionCode 5）· 新增「跟踪」Tab、无卡片股票/ETF 行情列表与详情页、OLED 纯黑深色模式及榜单显示修复

- 变更（新增「跟踪」功能与第二项导航 Tab）：原生 Android 底部与平板侧栏扩展为 `行情 / 跟踪 / 榜单 / 菜单` 四项（第二项「跟踪」含活跃跟踪圆点提示）。新增后端 `GET /api/app-tracking`、`POST /api/app-tracking/start`、`POST /api/app-tracking/cancel`（SQLite 读写分离 + 账号注销联动清理）与前端 `TrackingScreen`，对齐策略家模拟跟踪体验（每只 ¥10,000 模拟本金记账、六档植物成长阶段图标与标签、累计/当日盈亏与天数排序、置顶/自定义顺序调整、批量结束确认、快速 `+ 跟踪` 选股弹窗及暖琥珀「跟踪中」嫩芽胶囊）。
- 变更（「行情」改为无卡片股票/ETF 列表 + 上推详情抽屉）：新增后端 `GET /api/market/assets`（东方财富主源 + 新浪备用 + 排名趋势小线融合）与 `FallbackQuoteReader`（新浪批量/单只报价兜底）；Android「行情」首页改为无卡片 X × TradingView `0.5dp` 细分割线「股票 / ETF」资产列表，支持排序与快捷书签跟踪，点击任意资产或搜索结果打开无卡片 `AssetDetailScreen`（`AssetDetailSheetHost`），支持四周期 K 线/分时、结构解读与底部一键跟踪，返回保持原列表位置。
- 变更（OLED 纯黑深色模式对齐策略家）：`DarkChanPalette` 与 `values-night/themes.xml` 对齐策略货架 `X Lights Out` 纯黑视觉规范（`backgroundCanvas = #000000`、`cardSurface = #000000`、`subtleSurface = #16181C`、`cardBorder = #2F3336`）。
- 变更（榜单显示优化）：`RankingScreen` 使用顶部下划线分段切换「已确认同向 / 候选观察」，彻底移除每行内冗余的「已确认 / 候选」文字与徽标；将市场与买点标签下沉到副行，确保 4 个汉字以内的资产名称（如「贵州茅台」「宁德时代」）在窄屏下完整展示不截断（含 Robolectric 文本溢出自动化断言）。

## 2026-11-17 · 1.1.1（versionCode 4）· 菜单页对齐策略家无卡片极简细分割线排版与默认头像

- 变更（菜单页去卡片改版）：`AccountScreen.kt` 对齐策略家 `ProfileScreen` 改用通栏无卡片 `LazyColumn` + `0.5dp` 边缘分割线 + 分组标题 + `54dp` 扁平设置行，外网环境/安装方式/应用下载页统一收口进「环境与安装器」底部弹层（「缠论结构术语」快入条放在顶部资料区下方）。
- 变更（补齐默认头像）：未登录状态在 `44dp` 带描边圆形头像槽内绘制清晰的默认用户剪影头像；已登录状态自动提取昵称首字居中显示为高对比首字头像。
- 原因：用户反馈 `1.1.0` 菜单页仍保留外层卡片套框且缺少默认头像，按策略家最新版菜单布局完全对齐。

## 2026-11-17 · 原生 Android 澄境视觉重构、飞书邮箱注册与 ai:82 双通道更新

- 变更：参考策略货架 Android 澄境（Clarity）设计体系全面重构原生 Android 界面（`ChanGuanTheme` 浅色/深色、`RobotoCondensed` 等宽表格数字、`FixedHeaderPage`、`SegmentedControl`、`LedgerRow` 手风琴展开行、全屏 `StockSearchScreen` 浮层、澄境登录/注册弹窗、`行情 / 榜单 / 菜单` 底部与平板侧边导航），版本升级为 `1.1.0`（`versionCode = 3`）。
- 变更：服务端新增 `.env` 加载（`backend/app/env.py`），兼容飞书 `SMTP_*` / `EMAIL_CODE_PEPPER` 环境变量与邮件发送实现，启用邮箱验证码注册与密码登录；Android 端「菜单」支持在 `开发环境（AI外网）`（默认 `https://ai.oryjk.cn:82/chanlun-dev/`）、`开发环境（233内网）` 与 `生产环境` 间切换并按环境隔离登录会话。
- 变更：打通本机网关 `/chanlun-dev/` 与 `out109` Nginx `https://ai.oryjk.cn:82/chanlun-dev/`、`/chanlun-android/` 公网转发；升级客户端更新模块支持外网/内网自动回退、SHA-256 与签名校验、`releases.json` 历史展示、Shizuku 与系统安装双通道，并完善 `publish-apk.sh` 双入口校验。
- 原因：统一客户端干净克制的专业行情观感，并打通公网与内网一致的注册登录、API 访问和自动更新闭环。

## 2026-11-10 · 原生 iPhone 和 iPad 源码与 Apple 登录接入

- 变更：新增原生 App 源码及共享 Xcode 工程，使用 SwiftUI 页面和 UIKit 图表，包含搜索、四周期 K 线、缠论图层、MACD、信号证据、多级别联立、分时与收盘榜单；适配 iPhone/iPad 与前后台请求生命周期。
- 变更：新增独立 Apple 身份映射、单次 nonce 挑战、签名与 code 验证、Keychain/Bearer 会话、退出与删号、带时间核对的撤销通知和持久化撤销重试，保持原 H5 小程序登录兼容。
- 变更：提供跨 H5/Swift 的信号结论样例、原生 XCTest 源码、静态架构/Swift 语法检查、隔离存储的真实 HTTP 检查与 Mac/TestFlight 接续说明。社交仅保留组件扩展边界。
- 验证边界：当前机器没有 Swift/Xcode；原生类型检查、编译、XCTest、真机与 TestFlight 尚未执行，不将静态检查视为 App 已可发布。
- 原因：需要将现有 H5 功能复刻到原生 iPhone/iPad，先以少量用户进行 TestFlight 内测，Apple 登录先行，保留 Python 后端及未来图上内容的扩展能力。

## 2026-11-10 · 补查端点起历史收盘越界，禁止失效后自动复活

- 变更：质量评价扫描端点起的现有已收盘 K 线，记录首次严格越界日期与收盘价，价格恢复后仍保持失效；末根未收盘不会抹掉已有失效事实。
- 变更：榜单只接纳参考区间已核对且无已知越界的同向确认结构；旧版未核查数据暂列观察。页面区分已核对、曾越界和未知，继续保留真实识别/确认时间未知。
- 变更：规则版本升级为 4，沿用后台版本补算和旧快照保留机制；回归覆盖买/卖越界后恢复、盘中触及、等于边界、未完成 K 线及缺失端点区间。
- 验证：11-09 第 4 版已发布，6,943 只中成功分析 6,934 只；独立核对已确认组 155 只（偏多 46、偏空 109）的 7,417 根日 K 线，原 20 只历史越界信号全部转入观察，未发现同类遗漏。
- 原因：此前仅比较末根收盘，遗漏了设计要求的已知失效不复活；对 11-09 第 3 版的 175 只确认结构复核，20 只可从现有 K 线直接查出历史收盘越界，不需要生命周期库才能排除。

## 2026-11-10 · 统一信号排序参考，补充端点年龄与版本边界

- 变更：组内距离分档改用信号自身失效参考，缺少参考时不借用最新中枢的参考价。
- 变更：榜单及个股显示距结构端点的已收盘 K 线根数，按完整行情计算、排除未收盘线；旧数据缺字段时显示待计算，不设置未经验证的年龄上限。
- 变更：当前版本集中为 3，运行时读写版本必填，新对象默认当前版本；缺版本的历史数据仍按 1 解码，版本 2 保持原值并允许后台补齐新字段。
- 原因：修复审阅发现的排序口径不一致，避免长期历史信号被误认为近期机会，以及漏传版本参数造成的隐式混用。

## 2026-11-10 · 候选与已确认结构分组，纠正端点日期语义

- 变更：候选点不再显示「可关注买入」，榜单由后端统一将已确认且当前未失效的同向结构排在前组，其余单列观察；候选不再构成已确认的多级别印证。
- 变更：买卖点日期明确标为结构端点，首次识别与确认时间无法追溯时直说未知；候选明确尚未确认，不将历史端点日期当成可交易时刻。
- 变更：按一、二、三类点保留其自身结构失效参考，区分盘中触及和收盘严格越界，展示 MACD 及前段/回抽量比作为待验证证据，不宣称成功概率。
- 变更：收盘快照增加规则版本，同日升级可补算一次，检查点按版本隔离，更新期间保留旧快照，页面 GET 仍只读。
- 原因：旧结论和排序没有检查信号确认状态，候选容易被当作即时交易机会；端点可能早于实际识别日期，需要以稳健优先方式区分结构、证据与执行。

## 2026-11-10 · 明确榜单数据日期与信号日期

- 变更：榜单顶部加重显示收盘日期，将完整更新时间移至顶部并固定显示北京时间，保留覆盖数、更新进度与失败提示。
- 变更：买卖点标注为「买点信号出现于 / 卖点信号出现于」，相对涨跌标为「较信号价」，补充行情截止日期说明，适配手机与桌面布局。
- 原因：历史买卖点日期容易被误认为榜单的数据日期，导致用户以为每日收盘榜单没有更新。

## 2026-11-04 · 榜单与个股页展示直接结论、买卖点日期涨跌、走势图及游龙加载动效

- 变更：结构榜单与个股结构解读直接展示结论（如「可关注买入」「已涨高，不宜追买」「刚突破中枢，等回踩买点」「卖点附近，宜规避」），以及失效参考价距现价的涨跌百分比。
- 变更：榜单筛选排序按操作结论层级优先（优先展示「可关注买入 / 卖点附近，宜规避」标的，再按最新信号日期与结构强度排序），每榜默认展示 10 只并提供「展开更多」与实时搜索（支持代码、名称、科创、结论、确认状态）。
- 变更：榜单每行展示最近买点与卖点出现日期、确认状态（`已确认` / `待确认`）以及当前价格相对于该日期的涨跌幅；中间区域绘制日线价格走势图并标注买卖点价格与日期；科创板证券（`688`/`689`）代码旁展示「科创」标识。
- 变更：新增「游龙巡盘」SVG 动态加载组件（`DragonLoader`），接入榜单、K 线图与实时报价加载态；网页标题更新为「缠观 · 量化结构分析」。

## 2026-11-04 · 榜单构建提速与中断恢复

- 变更：K 线主备切换增加熔断，主源连续 3 次不可用后 5 分钟内直接使用备用源，到期试探主源一次；全量构建由约 6.5 只/秒提升到约 8.7 只/秒。
- 变更：进程停止导致的构建中断改为释放租约并保留检查点，状态回到准备中，下次启动立即续算，不再进入 15 分钟失败退避。
- 变更：新浪证券清单单页偶发返回空时有限重试（3 次，递增等待），避免整轮清单失败。

## 2026-11-04 · 收盘榜单构建时间提前到 15:30

- 变更：`CHANLUN_RANKING_RUN_AT` 默认值由 16:00 改为北京时间 15:30，仍要求不早于 15:00。
- 原因：收盘后更早给出当日榜单。

## 2026-11-04 · 收盘榜单增加新浪备用证券清单

- 变更：新增新浪行情中心全市场清单读取（沪 A、深 A、ETF 分页并逐节点核对数量），东方财富清单不可用时自动切换。
- 原因：本机访问东方财富 `clist` 清单与 `push2his` K 线接口均返回 502，榜单无法取得完整清单；新浪清单可用，K 线已有新浪备用。
- 验证：新浪清单 6,940 只（沪 A 2,317、深 A 2,906、ETF 1,717），抽样核对收盘价与涨跌幅；11-03 停牌证券因无当日数据计入失败。

## 2026-11-04 · 新浪备用 K 线改为前复权

- 变更：新浪备用 K 线按新浪前复权因子（`qfq.js`，按北京时间自然日缓存）换算，与东方财富主源 `fqt=1` 口径一致；因子不可用时报错而不静默返回不复权价格。
- 原因：不复权价格在除权除息日产生人为跳空，会误生成向下笔、破坏中枢并误判三类卖点，MACD 也随之失真；前复权保持最新价等于真实价，历史走势连续。
- 处理：已作废按不复权价格构建的 11-03 榜单（备份于 `var/rankings/backup/`），按前复权重新构建。

## 2026-11-03 · 全市场榜单改为每日收盘后构建

- 变更：取消前 40 只限制，新增沪深 A 股和境内 ETF 完整目录分页与校验；日线仅使用指定收盘日数据并绕过盘中缓存。
- 变更：默认北京时间 16:00 后后台构建最近收盘日榜单，同日完成后不重复；增加 SQLite 快照、租约、逐证券检查点与有限重试，页面 GET 和刷新只读结果。
- 变更：页面展示收盘日期、覆盖数量和更新状态，首次准备与真正空榜区分，取数失败时保留旧结果。
- 原因：用户只看收盘后的日线结果，需要覆盖全市场，同时避免每次打开页面都等待全量计算。
- 验证边界：真实完整清单请求当前失败，首轮全范围扫描尚未完成；不将候选样本当作全市场结果发布。
## 2026-11-03 · 界面改为行情软件版式

- 变更：移除新拟态凹凸阴影和卡片式布局，手机端改为白底通栏、灰色分隔带；主色由薄荷绿改为墨蓝，红绿只表示涨跌与买卖；压缩报价头让 K 线进入首屏；周期改为下划线标签，图层开关以图上笔触作图例；笔中枢标签置顶显示不再被 K 线遮挡；文案去除中点拼接；价格按证券类型统一格式化，股票 2 位、ETF 3 位。视觉规范写入 `AGENTS.md`。
- 原因：原界面层级扁平、图表位置靠下，绿色主色与「绿跌」语义冲突，整体观感模板化，不像专业行情工具。

## 2026-11-03 · 修复深市榜单候选被误过滤

- 变更：候选池转换时保留数字市场编号 0，恢复深市股票和 ETF；增加混合市场转换、无效市场拒绝及 40 只候选完整参与分析的回归测试。
- 原因：原转换将数字 0 当成空值，40 只候选中的深市证券被提前过滤，导致榜单覆盖不足。

## 2026-11-03 · 清理面向用户的数据来源说明

- 变更：移除分析周期下的 K 线数量与行情供应商说明、悬浮来源提示，以及顶部「公开行情」标识；行情异常提示改为通用文案。
- 原因：行情来源属于内部实现信息，用户界面只需展示行情和分析结果，避免额外信息干扰。

## 2026-11-03 · 修复 K 线加载时页面抖动

- 变更：手机端将证券名称与代码独立为一行，报价和周期信息使用固定列；约束数据源说明的宽高，统一标题及涨跌占位文案的行高，长名称保留完整标题提示。
- 变更：增加浏览器布局回归检查，覆盖 320–1440px 下首次加载、代码解析为名称、重复刷新与周期切换时搜索栏和图表的坐标及尺寸。
- 原因：数据源说明在载入后变长，会挤压证券标题换行；占位符与实际数据使用的字体行高也不一致，使图表上下跳动。

## 2026-11-03 · 移除榜单重复刷新按钮

- 变更：删除结构榜单标题旁的刷新按钮，统一使用右上角入口；保留计算状态提示，右上角按钮按当前页面显示刷新说明。
- 原因：同一榜单存在两个刷新入口，页面内按钮占用标题区域且功能重复。

## 2026-11-03 · 行情与榜单改为底部导航

- 变更：将行情、榜单从顶部移至固定底部 tabbar，增加图标和当前页面状态，适配 iPhone 底部安全区；内容预留底部空间，切换页面时回到顶部并保留股票和周期。
- 原因：方便手机单手切换页面，释放顶部品牌和账号区域的空间。

## 2026-11-03 · 更新缠观品牌标志

- 变更：将左上角三条斜线替换为确认的青绿色「走势折线 × 观察窗口」SVG，调整字标字重和移动端尺寸，并同步浏览器标签页图标；品牌首页链接适配部署前缀，窄屏避免导航挤压换行。
- 原因：让品牌标志呼应缠论结构分析，并使其在导航栏和小尺寸图标中清晰显示。

## 2026-11-03 · 修复旧浏览器行情白屏并增加开发启动诊断

- 变更：行情结构、背驰、分时合并及实时图表改用下标读取数组末项，避免依赖 Array.prototype.at；增加缺少该方法时的回归测试。
- 变更：Vite 开发服务在应用模块前注入启动错误提示，空页面直接显示脚本错误与浏览器信息，隐藏 webview 桥接参数；正式构建不注入该脚本。
- 原因：模拟旧浏览器缺少 Array.at 时，行情返回后会报错并使 React 清空整个页面；微信开发者工具自身调试窗口的报错不足以定位网页问题，需要在开发页内保留启动诊断。
