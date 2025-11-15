# fiber-gateway-example 模块

## 模块职责
`fiber-gateway-example` 展示如何使用 Proxy、Server、Script 等模块搭建可热更新的脚本化网关，包括配置监听、指标采集与统计拦截器。示例入口 `Main` 负责解析命令行、加载配置目录并启动 Engine。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L32-L95】

## 关键流程
1. **命令行解析**：`Main` 从参数读取脚本目录，校验存在性后构造 `DirectoryFilesConfigWatcher`，并将自定义 `Module` 与指标、统计拦截器绑定到引擎。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L34-L94】
2. **引擎启动**：通过 `LibProxyMainModule.createEngine()` 创建 Engine 并安装 HttpServer；随后调用 `engine.runLoop()` 进入主循环。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L66-L95】
3. **配置监听**：`DirectoryFilesConfigWatcher` 定期扫描目录，解析 `.json/.js` 文件构建 `UrlHandlerManager`，将脚本项目注册到 HttpServer，支持热更新与资源销毁。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/DirectoryFilesConfigWatcher.java†L28-L131】
4. **脚本加载**：`UrlHandlerManager` 使用 `LibProxyMainModule.createProjectInjector()` 构建项目级容器，编译脚本并维护引用计数，确保旧版本在被替换时正确销毁。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/route/UrlHandlerManager.java†L13-L70】

## 配套组件
- **MetricRouteHandler**：向 `/metric` 暴露 Prometheus 指标，结合 `PrometheusMeterRegistry` 采集网关指标并通过 `RateLimiter` 防止抓取过载。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/MetricRouteHandler.java†L18-L62】
- **StatisticInterceptor**：作为 HttpServer 拦截器，统计请求时延与 body 大小，并将指标注册到 Micrometer。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/StatisticInterceptor.java†L18-L113】
- **LibSleepFunc/常量库**：示例中通过 `HttpLibConfigure` 注入额外脚本函数与 `$req.*` 常量，演示如何扩展脚本运行时。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L48-L94】

## 自定义指南
- 替换 `DirectoryFilesConfigWatcher` 为自定义实现，即可接入配置中心或数据库，同时复用 `LibProxyMainModule` 的项目装配流程。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/DirectoryFilesConfigWatcher.java†L57-L131】
- 可在 `Main` 的绑定逻辑中扩展更多 `Module`（如安全、审计），或注册新的 Router/Interceptor 以满足业务需求。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L48-L94】
- 若需进一步观测，可在 `MetricRouteHandler` 内部注册更多 Micrometer 指标或导出额外格式。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/MetricRouteHandler.java†L18-L62】

## 与核心模块的关系
- 依赖 `LibProxyMainModule` 提供的 Engine 装配与脚本库注入能力。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L48-L95】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L29-L185】
- 使用 `fiber-gateway-server` 的 HttpServer 拦截器与路由能力实现动态项目挂载。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/DirectoryFilesConfigWatcher.java†L76-L131】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L95-L138】
