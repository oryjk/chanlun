# 缠观 Android 原生客户端

`android/` 是缠观的原生 Android 客户端（手机与平板自适应），采用 **100% Kotlin + Jetpack Compose + 原生 Canvas K 线/分时图表** 实现，严格遵循项目整洁架构规范（`Presentation -> Application -> Domain`，`Infrastructure` 实现端口，`App` 为唯一组合根）。

## 一、架构与目录

```text
android/app/src/main/java/cn/oryjk/changuan/
├── domain/                    # 不可变行情/信号模型、环境/主题配置模型、更新模型与自更新校验、北京时间与交易时段、信号结论、榜单分组、分时合并、视窗与价格投影
├── application/               # 行情读取 Reader、报价采样 Writer、环境/主题/会话存储端口、更新读取/下载/安装端口及用例
├── infrastructure/
│   ├── update/                # AppUpdateChecker (外网->内网回退 + releases.json)、ApkDownloader (SHA-256)、ApkInstaller (Shizuku + FileProvider)
│   └── ...                    # OkHttp 传输、显式 JSON DTO 映射边界（MarketGateway / AuthGateway）、分环境 SharedPreferences 持久化
├── presentation/
│   ├── Theme.kt               # 澄境（Clarity）浅色（#FFFFFF）与 X Lights Out OLED 纯黑深色（#000000）调色板
│   ├── designsystem/          # FinancialNumber (RobotoCondensed tnum)、PageHeader、StarkUnderlineTabs、SegmentedControl、LedgerRow、TrackingComponents (跟踪中胶囊 / 六档成长图标 / 详情抽屉宿主)、MainBottomBar/MainSideRail
│   ├── AppViewModel.kt        # 状态流、行情列表/详情抽屉/用户跟踪管理、请求 generation 与认证 authEpoch 并发防护、30 秒自动巡检更新与多环境切换
│   ├── charts/                # 原生 Canvas K 线 + 成交量 + MACD 共享视窗图表（文字图层最后绘制）与分时曲线
│   └── screens/               # 自适应主界面（行情 / 跟踪 / 榜单 / 菜单）、无卡片详情页 AssetDetailScreen、结构解读面板、搜索全屏浮层、邮箱登录/注册弹窗、环境/安装设置与更新历史弹窗
└── app/                       # AppContainer 唯一组合根（按环境重建服务）、ChanGuanApplication 与 MainActivity
```

## 二、登录与注册流程

Android 端采用 **账号密码登录 + 邮箱验证码注册**，同时保留游客模式（不登录可使用全部行情、四周期缠论结构、多级别联立、实时分时与全市场收盘榜单功能）：

- `POST /api/app-auth/email/code`：发送 6 位数字邮箱验证码（10 分钟有效、60 秒发送冷却，未注册邮箱校验）。
- `POST /api/app-auth/email/register`：校验邮箱验证码并设置密码（`scrypt` 哈希存储），创建独立 UUID 账号并签发 30 天随机 Bearer 会话令牌。
- `POST /api/app-auth/email/login`：使用邮箱与密码登录并签发 30 天 Bearer 会话令牌。
- `GET /api/app-auth/session`：恢复当前原生登录状态。
- `POST /api/app-auth/logout` / `POST /api/app-auth/delete-account`：退出当前会话或彻底删除账号与全部登录会话。

服务端配置环境变量（由根目录 `.env` 自动加载，兼容飞书 SMTP 配置键名）：
- `CHANLUN_EMAIL_AUTH_ENABLED=true`
- `CHANLUN_SMTP_HOST` / `SMTP_HOST`、`CHANLUN_SMTP_PORT` / `SMTP_PORT`、`CHANLUN_SMTP_USERNAME` / `SMTP_USERNAME`、`CHANLUN_SMTP_PASSWORD` / `SMTP_PASSWORD`、`CHANLUN_SMTP_FROM` / `SMTP_FROM_EMAIL`、`CHANLUN_SMTP_TLS_MODE` / `SMTP_TLS_MODE`、`EMAIL_CODE_PEPPER`
- `CHANLUN_APP_AUTH_SECRET`（至少 32 字符）

## 三、本地构建与环境配置

1. 确保已安装 JDK 17 与 Android SDK（`compileSdk = 35`，`minSdk = 26`），并在 `android/local.properties`（不入库）中配置 `sdk.dir`。
2. 应用在「菜单 → API 服务环境」支持运行时切换并在各环境间独立保存登录态：
   - **开发环境（AI外网）**（默认）：`https://ai.oryjk.cn:82/chanlun-dev/`
   - **开发环境（233内网）**：`http://172.16.60.233:8000/`
   - **生产环境**：`https://match.oryjk.cn/chanlun/`
3. 运行单元测试（含架构守护、更新校验与 Robolectric 澄境截图测试）并构建 Debug APK：

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

生成的安装包位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 四、双通道 30 秒自动检测更新与 7T 硬盘发布流程

- **7T 硬盘物理存储路径**：`/data7t/apk-downloads/chanlun-android`（由本机内部网关容器 `betalpha-internal-gateway-nginx-1` 挂载提供内网访问，并经 `out109` Nginx `ai.oryjk.cn:82` 反向代理提供公网 HTTPS 访问）。
- **访问与更新地址**：
  - 外网下载页与更新主通道：`https://ai.oryjk.cn:82/chanlun-android/`（`metadata.json`、`releases.json`、`latest.apk`）
  - 内网导航主页与回退通道：`http://172.16.60.233/` / `http://172.16.60.233/chanlun-android/`
- **安装能力**：下载完成后校验 SHA-256 与当前安装包的包名及签名，支持 Shizuku 静默提交安装并自动回退系统 `FileProvider` 标准包安装器。
- **每次修改完任务后的强制发版步骤**：
  1. 在 `android/app/build.gradle.kts` 中递增 `versionCode`（+1）并更新 `versionName`。
  2. 运行 `cd android && ./gradlew testDebugUnitTest assembleDebug`。
  3. 提交并推送到 `origin/main`：`git add -A && git commit -m "..." && git push origin main`。
  4. 执行 `./android/scripts/publish-apk.sh --skip-build --note "本次更新说明"` 发布至 7T 硬盘，保留最近 10 个历史版本，并同步校验 `172.16.60.233` 与 `ai.oryjk.cn:82` 的 `metadata.json`、`Content-Length` 与 `Range: bytes=0-0`（HTTP 206）。

