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
│   └── services/              # 缠论等纯领域算法
├── application/
│   ├── ports/
│   │   └── readers.py         # 查询端口；写端口应独立放入 writers.py
│   └── queries/               # 只读用例；写用例应放入 commands/
├── infrastructure/
│   ├── http/                  # 通用传输客户端
│   ├── market/                # 东方财富、Yahoo 等读取适配器
│   └── cache/                 # 缓存装饰器，不污染用例
├── interfaces/api/            # FastAPI 路由、Pydantic DTO、异常映射
└── app/                       # 组合根与应用启动
```

### 后端读写分离

- Query 只能读取，不得产生数据库写入、修改配置或其他业务副作用。
- 新增写入功能时建立 `application/commands` 和 `application/ports/writers.py`，由 Command 调用 Writer。
- Reader 与 Writer 分开定义，不创建一个同时承担所有读写的巨型 Repository。
- 当前项目没有业务写入，不得为了形式完整创建空写库或无意义 Command。
- 缓存是读取适配器的装饰器；强制刷新只能绕过读取缓存，不得被当作业务写入。
- 新增数据源时实现对应端口，在 `backend/app/container.py` 注册；领域算法和 API 路由不应跟随修改。

## 三、前端目录与职责

```text
frontend/src/
├── domain/                    # 纯领域定义、选择器和业务规则
├── application/               # 股票搜索、快照读取等用例
├── infrastructure/api/        # fetch、HTTP 错误、DTO 转换和 Gateway
├── presentation/
│   ├── components/            # 渲染和用户交互
│   └── hooks/                 # 请求生命周期、加载状态、防抖等交互状态
├── app/container.js           # 前端唯一依赖装配位置
├── App.jsx                    # 展示层页面编排
└── main.jsx                   # 用例注入与 React 启动
```

### 前端读写分离

- React 组件和 Hooks 不得直接调用 `fetch`，必须调用 `application` 用例。
- 查询统一通过只读 Gateway；未来新增收藏、配置保存等写操作时，建立独立 Command 用例和写入 Gateway 方法，不得伪装成查询。
- `infrastructure/api` 是 API DTO 边界，第三方字段不得散落到组件中。
- 展示组件可以负责本地 UI 状态，但行情统计和可复用业务判断应放入 `domain`，请求编排应放入 `application` 或展示 Hooks。
- 新增用例在 `frontend/src/app/container.js` 装配并注入，不允许组件直接实例化基础设施。

## 四、变更检查

修改架构或目录职责时必须同步更新本文件。完成变更后至少执行：

```bash
uv run uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
cd frontend && bun run build
```

并通过真实 HTTP 请求验证股票与 ETF 搜索、四周期行情和错误响应；不得仅以导入成功或构建成功代替端到端验证。
