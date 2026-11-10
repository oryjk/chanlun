# 缠观原生 iPhone 和 iPad App

SwiftUI 页面与 UIKit 原生 K 线图，iOS/iPadOS 17 起，复用缠观 FastAPI API。包含搜索、四周期 K 线、缠论图层、MACD、结构质量和多级别联立、分时、收盘榜单以及 Apple 登录。

本次在无 Swift/Xcode 的 Linux 机器编写。后端和已有 H5 可在这里测试；原生源码的语法检查不能替代 Xcode 编译、XCTest 和真机验证。下方 Mac 检查项尚未执行，不能把当前源码视为已可上传 TestFlight 的构建。

## Mac 上打开

1. 获取包含本次提交的代码，安装支持 iOS 17 SDK 的 Xcode，并完成首次启动组件安装。
2. 在仓库根目录创建 `ios/Local.xcconfig`（已忽略，不提交）：

   ```xcconfig
   DEVELOPMENT_TEAM = YOUR_TEAM_ID
   CHANLUN_BUNDLE_ID = your.own.bundle.identifier
   CHANLUN_API_BASE_URL = https:/$()/your-host.example/chanlun/
   ```

   xcconfig 中的 `https:/$()/` 是避免 `//` 被当成注释的写法。服务地址需要保留部署子路径，结尾可带 `/`，客户端在其后追加 `api/...`。仓库默认地址沿用项目生产域名，部署是否已就绪需要实际验证。

3. 打开 `ios/ChanGuan.xcodeproj`，选择共享 Scheme **ChanGuan**，在 Signing & Capabilities 中选开发团队，确认唯一 Bundle ID 和 Sign in with Apple capability。Debug/Release 均会读取可选的本地配置。
4. 选择实际安装的 iPhone/iPad 模拟器执行 Build 和 Test。Release 只允许 HTTPS API；当前未放开任意 HTTP 的 ATS 例外。

命令行检查（将模拟器 ID 替换为机器上实际可用的值）：

```bash
xcodebuild -list -project ios/ChanGuan.xcodeproj
xcrun simctl list devices available
xcodebuild test -project ios/ChanGuan.xcodeproj -scheme ChanGuan \
  -destination 'platform=iOS Simulator,id=YOUR_SIMULATOR_ID' CODE_SIGNING_ALLOWED=NO
```

工程和共享 scheme 已入库，无需安装 XcodeGen/CocoaPods。新增 Swift 文件后可运行 `python3 ios/scripts/generate_project.py` 同步项目成员；本地团队与 Bundle ID 放在 xcconfig，不直接改生成的 pbxproj。

## Apple 登录后端配置

现有 H5 的小程序登录保持原方式，原生端使用独立账号映射与 Bearer 会话，不按邮箱合并用户。

```dotenv
CHANLUN_APPLE_ENABLED=true
CHANLUN_APPLE_CLIENT_ID=your.own.bundle.identifier
CHANLUN_APPLE_TEAM_ID=YOUR_TEAM_ID
CHANLUN_APPLE_KEY_ID=YOUR_KEY_ID
CHANLUN_APPLE_KEY_PATH=/private/path/AuthKey_YOUR_KEY_ID.p8
CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY=a-stable-random-secret-of-at-least-32-characters
CHANLUN_APP_AUTH_STORE_PATH=var/accounts/accounts.sqlite3
```

在 Apple Developer 中配置与 Bundle ID 对应的 App ID、Apple 登录能力和私钥。`CLIENT_ID` 必须与 App 的 Bundle ID 完全一致；本期原生登录不需要另造网页 OAuth 回调流程。将 Apple server-to-server 通知地址指向公开 HTTPS 的 `/chanlun/api/app-auth/apple-notifications`（实际部署前缀相应调整）。

未设置 `CHANLUN_APPLE_ENABLED=true` 时，不创建账号库，原生认证返回 503 和明确提示，游客行情与 H5 登录照常可用。启用时配置缺失会拒绝启动，避免使用临时账号加密密钥。账号数据库及加密密钥必须一起备份；更换密钥前需迁移已有密文。

后端新增端点：

| 方法与路径 | 用途 |
| --- | --- |
| POST `/api/app-auth/challenge` | 5 分钟、单次使用的登录挑战 |
| POST `/api/app-auth/apple` | 验证 Apple 签名/nonce 并兑换 code，返回内部用户及 30 天会话 |
| GET `/api/app-auth/session` | 只读会话状态，游客也可请求 |
| POST `/api/app-auth/logout` | 撤销当前 Bearer 会话 |
| POST `/api/app-auth/delete-account` | 删除账号、撤销全部会话并提交 Apple 撤销任务 |
| POST `/api/app-auth/apple-notifications` | 验签和去重处理 Apple 授权撤销通知 |

服务端只保存 App 会话摘要，Apple refresh token 加密存储。删除先在本地生效，Apple 撤销失败以加密待办重试，App lifespan 每分钟处理一次。停止应用不会清空重试队列。

## 分层与后续扩展

- `Domain/`：行情、结构与信号对象，格式化、交易时段和图表坐标规则。
- `Application/`：读写端口、行情与认证用例。
- `Infrastructure/`：URLSession/JSON、Keychain、Apple 授权、页面状态存储。
- `Presentation/`：页面状态、SwiftUI 页面、UIKit 图表与桥接。
- `App/`：唯一依赖装配与启动。

K 线渲染、坐标、视窗和触摸事件分别封装，图表不请求网络或感知账号。未来社交只是扩展方向，当前没有社交 API、表或界面。日期索引和结构 ID 不承诺是未来内容的稳定锚点，复权与跨周期定位届时单独设计。

## 在当前机器可运行的检查

```bash
uv run python -m unittest discover -s tests
python3 ios/scripts/check_sources.py
uv run --with tree-sitter --with tree-sitter-swift python ios/scripts/check_sources.py --syntax
cd frontend
bun test src
bun run build
```

## Mac 和 TestFlight 待验证清单

- [ ] Xcode Build 与全部 XCTest；先解决编译/SDK 问题，再进行功能验收。
- [ ] iPhone 和 iPad 横竖屏、iPad 窄分屏与旋转后的页面/证券状态。
- [ ] K 线主图、量、MACD 光标一致，拖动、双指缩放、长按、各图层与中枢文字层级。
- [ ] 搜索股票和 ETF、四周期、分时午休、前后台恢复、弱网与断网重试。
- [ ] 榜单首次准备/空榜/部分失败、日期时区、候选与确认分组、搜索与展开、点击证券。
- [ ] 真实 Apple 首次登录、再次授权缺姓名、取消、退出、删号和系统撤销授权。
- [ ] 后端使用稳定 HTTPS，Bundle ID/audience/Apple key/通知回调与所选开发团队匹配。
- [ ] 在 App Store Connect 创建 App，填写隐私政策与实际数据收集说明，核对 PrivacyInfo.xcprivacy。
- [ ] 调整版本与 build number，选择 Any iOS Device，Product → Archive，校验归档。
- [ ] 上传到 App Store Connect 后创建 TestFlight 测试组，按实际内部/外部测试方式完成所需流程。
- [ ] iPhone/iPad 真机从 TestFlight 安装并回归；完成这些步骤后才标为 TestFlight 可用。

当前没有执行上传、推送或线上配置修改。
