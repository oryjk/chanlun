# 缠观原生 App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可在 Mac 继续构建的原生 iPhone/iPad 源码、Apple 登录后端和明确的 TestFlight 交接说明。

**Architecture:** SwiftUI 页面、UIKit 图表、分离的 Domain/Application/Infrastructure/Presentation 与唯一组合根。共用现有行情 API，新增独立 App 账号与 Bearer 会话，保持原 H5 Cookie 登录兼容。

**Tech Stack:** Swift 5.9+、iOS/iPadOS 17、SwiftUI、UIKit、Foundation、AuthenticationServices、Security；Python/FastAPI、SQLite、PyJWT 与 cryptography。

**Spec:** `docs/superpowers/specs/2026-11-10-native-ios-design.md`，用户已确认。

**执行记录：** Tasks 1–8 的源码与测试已写入，Task 9 的工程、静态检查与交接说明已写入。后端/H5 和实际 HTTP 检查已运行；Swift 源码只执行语法及结构检查，Xcode 编译与原生测试仍按用户安排留给 Mac。逐步复核细节记录在本次执行 ledger；下方复选项保留为 Mac 接续时可复查的验收步骤，不将未执行的原生验证勾为通过。

## Global Constraints

- 直接在 main 开发；用户已授权写代码并提交，未授权推送或上传 TestFlight。
- 当前机器没有 Swift/Xcode；用户明确接受先交付源码再在 Mac 编译。原生单元测试写入工程，实际执行与真机验证列为待办；可运行的后端、前端和静态结构检查必须执行。
- 保留已有未提交重构。提交只包含本次文件与共享文件中本次新增的改动；不提交他人的改动，不回退工作区。
- iPhone/iPad 同一 target，最低版本 17，简体中文与现有行情语义色。
- 不新增社交功能、收藏跟踪或微信绑定；只分离图表渲染、手势与内容。
- 证券标识作为不透明字符串；不改现有行情 DTO 与 analysis_version，不依赖尚未完成的证券标识重构。
- Query 只读；nonce、Apple code 兑换、会话写入、登出和删号均为 Command。
- Apple 私钥、令牌和运行数据库不入库；未配置 Apple 时行情可用、原生认证明确返回不可用。
- 每项进行针对性验证，最后运行 AGENTS.md 要求的全套检查；Xcode 不可执行不得写成通过。

## Review Focus

1. 同一登录挑战并发提交只能消费一次，失败/重放不得创建第二个会话。Task 2 验证。
2. 删号或 Apple 撤销后旧 Bearer 必须失效，撤销网络错误不能恢复账号。Task 3 验证。
3. 切换证券、后台/前台和非上海设备时区不能产生迟到数据覆盖、重复轮询或日期偏移。Task 5 与 Task 8 验证。
4. 空行情、等价价格、候选、已失效、旧榜单缺字段不能导致图表崩溃或误标确认。Task 4 与 Task 6 验证。
5. iPad 窄窗口、横竖屏和连续缩放后主图/量/MACD 同根定位，文字覆盖层不被遮挡。Task 6 与 Mac 验证清单覆盖。

---

### Task 1: 固定边界与原生测试契约

**Files:** Create `ios/README.md`, `ios/ChanGuanTests/MarketRulesTests.swift`, `tests/test_native_auth.py`; modify `AGENTS.md` only for new App and auth responsibilities.

**Interfaces:** Native `MarketSnapshot`, `RankingBoard`, `QuoteSeries` mirror the public JSON but map to immutable Swift structs. Backend `AppIdentity`, `LoginChallenge`, `AppSession` use UUID user IDs and timezone-aware datetimes, independent of `UserIdentity`.

- [ ] Write failing backend tests for challenge expiry/replay, same subject stable identity, and H5 integer IDs remaining independent. Write Swift tests for ETF `1.234`, stock `1.23`, candidate/invalidated conclusions and market-time dates.
- [ ] Run `rtk uv run python -m unittest discover -s tests -p test_native_auth.py`; expect missing native auth feature to fail. Swift execution is explicitly deferred to Mac.
- [ ] Add immutable backend models, application ports and test fixtures as Task 2 needs them; keep Apple SDK/HTTP/SQLite outside inner layers.

### Task 2: Apple verification and transactional login

**Files:** Create `backend/domain/app_auth.py`, `backend/application/ports/app_auth.py`, `backend/application/commands/app_login.py`, `backend/infrastructure/auth/apple_gateway.py`, `backend/infrastructure/persistence/sqlite_app_auth.py`; modify `pyproject.toml`, `uv.lock`.

**Interfaces:** `IssueAppChallenge.execute() -> LoginChallenge`; `LoginWithApple.execute(challenge_id, identity_token, authorization_code, nickname=None) -> AppLoginResult`; `AppleGateway.verify_and_exchange(token, code, nonce) -> AppleIdentity`. Store ports expose atomic challenge consume, identity lookup/create and session issuance.

- [ ] Add tests for expired/wrong nonce, forged signature, wrong issuer/audience/algorithm, parallel consume, same Apple subject, missing name and token hash storage. Use generated test keys and fake HTTP transport; no real Apple secrets.
- [ ] Implement 5-minute challenges with random nonce and state; client passes SHA256(nonce) in Apple request; server validates that claim. Allow only RS256 Apple identity tokens; client secret uses ES256 with configured Apple key.
- [ ] Exchange code server-side; verify returned token has same subject and audience, encrypt Apple refresh token with Fernet, issue random 30-day opaque session and store only its SHA256 digest. Persist all state with SQLite transactions and explicit Reader/Writer methods.
- [ ] Run focused tests; expect all named login and crypto cases pass. No network request should occur for invalid local input.

### Task 3: Native session, deletion and API composition

**Files:** Create `backend/application/queries/app_session.py`, `backend/application/commands/app_account.py`, `backend/interfaces/api/app_auth_routes.py`, `backend/app/app_auth.py`, `tests/test_native_auth_api.py`; modify `backend/app/main.py`, `.gitignore`, `AGENTS.md`.

**Interfaces:** `/api/app-auth/challenge` POST; `/api/app-auth/apple` POST; `/api/app-auth/session` GET; `/api/app-auth/logout` POST; `/api/app-auth/delete-account` POST; `/api/app-auth/apple-notifications` POST. Responses expose `{user_id,nickname}` and native token/expiry only at login; guest session reports `authenticated:false`.

- [ ] Write tests for session/logout/deletion/disabled config, invalid Apple notification, duplicate notification, and concurrent deleted-account login. `GetAppSession.execute(token) -> AppIdentity | None` is read-only.
- [ ] Add composition root with env-based configuration, separate SQLite account file, read/write ports, encrypted revocation queue and bounded retry. Delete locally and revoke sessions atomically before external revocation; retain only encrypted revocation job when Apple is unavailable.
- [ ] Validate signed Apple server notifications before consuming event IDs and revoking account sessions. Retry pending revocations from App lifespan without changing ranking worker ownership. Unconfigured native auth returns 503; H5 auth stays on existing routes.
- [ ] Run focused API tests plus full Python suite. Verify actual HTTP guest session, disabled auth, invalid request, CORS and existing H5 routes.

### Task 4: Native domain and API adapters

**Files:** Create `ios/ChanGuan/Domain/{MarketModels,SignalRules,MarketTime,ChartGeometry}.swift`, `ios/ChanGuan/Application/{MarketUseCases,AuthUseCases}.swift`, `ios/ChanGuan/Infrastructure/{APIClient,MarketGateway,AuthGateway,KeychainSessionStore}.swift`, `ios/ChanGuanTests/{DTOTests,ChartGeometryTests}.swift`.

**Interfaces:** `MarketReader` async throwing search/snapshot/quote/series/rankings/confluence methods, independent `QuoteWriter.record(_:)`; `AuthReader.session()` and `AuthCommands` challenge/login/logout/delete; `MarketUseCases`/`AuthUseCases` injected into presentation. API root configurable including `/chanlun/` prefix.

- [ ] Write XCTest cases for DTO snake_case keys, ISO8601 with/without fractions, null quality, empty boards and unknown enum values; geometry tests for constant price and empty series.
- [ ] Implement explicit DTO decoding/mapping, cancel-aware URLSession transport, Chinese HTTP errors, Keychain-backed native token, optional old fields with observation defaults. Match H5 conclusion precedence and ETF precision.
- [ ] Implement pure chart viewport/time and price projection plus trading-minute mapping; domain imports Foundation only. No network or SwiftUI inside domain/application.
- [ ] Run source structure checks here; run full XCTest on Mac using Task 9 instructions. Record missing compiler honestly.

### Task 5: App composition, adaptive navigation and request lifecycle

**Files:** Create `ios/ChanGuan/App/{ChanGuanApp,AppContainer}.swift`, `ios/ChanGuan/Presentation/{AppModel,RootView,StockSearchView,Theme}.swift`, `ios/ChanGuanTests/RequestLifecycleTests.swift`.

**Interfaces:** `@MainActor AppModel` owns selected stock, timeframe, tab, request generations and tasks; `AppContainer` alone creates adapters. Views invoke use cases/model actions.

- [ ] Write tests using controlled async readers for A→B switch with A finishing late, cancel/refresh, session restore and stop/start lifecycle.
- [ ] Implement iPhone tabs and iPad split navigation based on horizontal size class, compact width collapse, scenePhase observation, stable selection and debounced search. Initialize default security through existing search support; `secid` never parsed in UI.
- [ ] Use generation checks plus cancellation before applying responses. Poll only visible active screen, resume with fresh trading-time decision and prevent overlapping quote writes.
- [ ] Static architecture check must pass; simulator tests remain Mac gate.

### Task 6: UIKit chart rendering and native interaction

**Files:** Create `ios/ChanGuan/Presentation/Charts/{MarketChartView,MarketChartRenderer,MarketChartBridge,RealtimeChartView}.swift`, `ios/ChanGuanTests/ChartGeometryTests.swift` additions.

**Interfaces:** `ChartContent` accepts candle/analysis/MACD/signal immutable data; `ChartViewport` and `ChartProjection` handle coordinates; `ChartSelection` returns time/index/price; SwiftUI bridge forwards state and selection without network.

- [ ] Extend tests for price↔coordinate round-trip, viewport clamping, all-equal prices, single candle, and resize preserving selected timestamp.
- [ ] Draw candles, volume, MACD, fractals, pens, segments, zones and signals with fixed semantic colors; confirmed/candidate line styles differ; labels draw last. All three plots share one viewport.
- [ ] Add pan, focal-point pinch, long-press crosshair and accessible selected-bar detail. Handle touches independent of business content; expose selection callback for future content without social types.
- [ ] Implement realtime chart using market-local trading-minute axis, lunch separator and quote labels. Static syntax/architecture checks here; gestures and screenshots on Mac/iPhone/iPad.

### Task 7: Analysis, evidence and ranking screens

**Files:** Create `ios/ChanGuan/Presentation/{MarketView,SignalPanel,RankingView,QuoteView,TermInfoView,LoadingView}.swift` and corresponding model tests.

**Interfaces:** Uses Task 4 domain selectors and Task 5 state; domain `RankingGroups`, `SignalConclusion` power UI; ranking selection navigates to daily snapshot.

- [ ] Write tests for confirmed/observation split, search, 10-item expansion, pending vs genuine empty board, and historical invalidation after recovery.
- [ ] Build quote header, period tabs, graph layers, structure summary, latest signals, evidence and four-level confluence. Preserve endpoint/unknown-detection semantics and disclaimer.
- [ ] Build ranking dates, update progress/errors/coverage, two biases with two signal groups each, sparkline and buy/sell markers, expand/search and stock navigation. Use server order unchanged.
- [ ] Build realtime fields, glossary presentation and dragon-like vector loading; keep layout flat and consistent with existing visual rules.

### Task 8: Native Apple authorization and account UI

**Files:** Create `ios/ChanGuan/Infrastructure/AppleAuthorization.swift`, `ios/ChanGuan/Presentation/AccountView.swift`, `ios/ChanGuanTests/AuthFlowTests.swift`; update AppContainer and AppModel.

**Interfaces:** `AppleAuthorizer.authorize(nonce:state:) async throws -> AppleCredential`; `AuthUseCases.login()` performs challenge, Apple request and server login, stores session only after successful exchange. Apple/framework objects stay in infrastructure.

- [ ] Write tests for cancel, missing name, wrong state, Keychain error, expired/restored session, logout offline and deletion retry UI.
- [ ] Implement AuthenticationServices delegate lifetime, presentation anchor, SHA256 nonce, returned state validation and optional name. On credential revoked/unknown state transition to guest and clear local token.
- [ ] Build sign-in button, account display, logout and explicit delete confirmation. On logout clear local token even if network fails and explain server logout retry; on deletion failure retain session so user can retry, and never pretend deletion completed.
- [ ] Run server auth suite and static client checks. Real Apple authorization remains Mac/device verification with configured team and keys.

### Task 9: Xcode project and Mac handoff

**Files:** Create `ios/ChanGuan.xcodeproj/project.pbxproj`, shared scheme, `ios/ChanGuan/{Info.plist,ChanGuan.entitlements,Assets.xcassets}`, `ios/Config/{Debug,Release}.xcconfig`, `ios/scripts/check_sources.py`, `ios/README.md` completion; modify project memory docs.

**Interfaces:** Scheme `ChanGuan` builds one universal iPhone/iPad app and XCTest target. Bundle ID/team/API root are configuration values with documented examples, no private credentials. Add `ios/Local.xcconfig` to ignores and import it optionally.

- [ ] Add source architecture checks and parse all Swift with a Swift grammar if compiler unavailable; plist/project structure verification must cover all source/test file membership and shared scheme references.
- [ ] Set iOS 17 deployment target, Swift 5 mode, automatic signing, supported device family 1/2, scene configuration, Apple sign-in entitlement, HTTPS root and API prefix preservation. No HTTP exception in Release.
- [ ] Document: clone/open project, set local team/Bundle ID/base URL, enable Apple capability, run `xcodebuild -list`, select actual simulator IDs, test on phone/tablet, archive, then TestFlight upload. Mark these Mac gates unchecked.
- [ ] Run full Python/JS suites, Vite build, isolated real HTTP smoke for stock/ETF search, four timeframes/confluence, rankings, quote and sample, H5 guest/code flow, native auth and errors/CORS. Use fake upstream HTTP only for controlled auth success; report live upstream failures separately.
- [ ] Request one fresh review, address material findings, update FEATURES/CHANGELOG and commit only this feature. User continues signing/compilation/TestFlight on Mac; no push or upload here.
