# 缠观 Android 原生客户端

`android/` 是缠观的原生 Android 客户端（手机与平板自适应），采用 **100% Kotlin + Jetpack Compose + 原生 Canvas K 线/分时图表** 实现，严格遵循项目整洁架构规范（`Presentation -> Application -> Domain`，`Infrastructure` 实现端口，`App` 为唯一组合根）。

## 一、架构与目录

```text
android/app/src/main/java/cn/oryjk/changuan/
├── domain/                    # 不可变行情/信号模型、北京时间与交易时段、信号结论、榜单分组、分时合并、视窗与价格投影
├── application/               # 行情读取 Reader、报价采样 Writer、账号读写端口、本地会话与路由存储端口及用例
├── infrastructure/            # OkHttp 传输、显式 JSON DTO 映射边界（MarketGateway / AuthGateway）、SharedPreferences 持久化
├── presentation/
│   ├── Theme.kt               # 与 H5 / iOS 统一的行情配色规范
│   ├── AppViewModel.kt        # 状态流、请求 generation 与认证 authEpoch 并发防护、前后台生命周期管理
│   ├── charts/                # 原生 Canvas K 线 + 成交量 + MACD 共享视窗图表（文字图层最后绘制）与分时曲线
│   └── screens/               # 自适应主界面、行情页、结构解读面板、收盘榜单页、邮箱密码登录/邮箱验证码注册页、搜索与术语弹窗
└── app/                       # AppContainer 唯一组合根、ChanGuanApplication 与 MainActivity
```

## 二、登录与注册流程

Android 端采用 **账号密码登录 + 邮箱验证码注册**，同时保留游客模式（不登录可使用全部行情、四周期缠论结构、多级别联立、实时分时与全市场收盘榜单功能）：

- `POST /api/app-auth/email/code`：发送 6 位数字邮箱验证码（10 分钟有效、60 秒发送冷却，未注册邮箱校验）。
- `POST /api/app-auth/email/register`：校验邮箱验证码并设置密码（`scrypt` 哈希存储），创建独立 UUID 账号并签发 30 天ランダム Bearer 会话令牌。
- `POST /api/app-auth/email/login`：使用邮箱与密码登录并签发 30 天 Bearer 会话令牌。
- `GET /api/app-auth/session`：恢复当前原生登录状态。
- `POST /api/app-auth/logout` / `POST /api/app-auth/delete-account`：退出当前会话或彻底删除账号与全部登录会话。

服务端配置环境变量：
- `CHANLUN_EMAIL_AUTH_ENABLED=true`
- `CHANLUN_SMTP_HOST`、`CHANLUN_SMTP_PORT`（默认 `465`）、`CHANLUN_SMTP_USERNAME`、`CHANLUN_SMTP_PASSWORD`、`CHANLUN_SMTP_FROM`、`CHANLUN_SMTP_TLS_MODE`（`tls` 或 `starttls`）
- `CHANLUN_APP_AUTH_SECRET`（至少 32 字符）

## 三、本地构建与测试

1. 确保已安装 JDK 17 与 Android SDK（`compileSdk = 35`，`minSdk = 26`），并在 `android/local.properties`（不入库）中配置：

```properties
sdk.dir=/path/to/Android/Sdk
# 可选覆盖后端地址（默认 https://match.oryjk.cn/chanlun/）
CHANLUN_API_BASE_URL=https://match.oryjk.cn/chanlun/
```

2. 运行单元测试（含整洁架构依赖守护测试）并构建 Debug APK：

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

生成的安装包位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 四、30 秒自动检测更新与 7T 硬盘发布流程

- **7T 硬盘物理存储路径**：`/data7t/apk-downloads/chanlun-android`（由本机内部网关容器 `betalpha-internal-gateway-nginx-1` 挂载至 `/usr/share/nginx/html/chanlun-android:ro`）。
- **本机 Nginx 访问地址**：
  - 内部导航主页：`http://172.16.60.233/`（点击「缠观 Android」）
  - 专属下载页：`http://172.16.60.233/chanlun-android/`
  - 最新 APK 直链：`http://172.16.60.233/chanlun-android/latest.apk`
  - 版本检测元数据：`http://172.16.60.233/chanlun-android/metadata.json`
- **每次修改完任务后的强制发版步骤**：
  1. 在 `android/app/build.gradle.kts` 中递增 `versionCode`（+1）并更新 `versionName`。
  2. 运行 `cd android && ./gradlew testDebugUnitTest assembleDebug`。
  3. 提交并推送到 `origin/main`：`git add -A && git commit -m "..." && git push origin main`。
  4. 执行 `./android/scripts/publish-apk.sh --skip-build --note "本次更新说明"` 发布至 7T 硬盘并校验线上 `metadata.json`。

