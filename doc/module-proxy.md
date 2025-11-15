# fiber-gateway-proxy 模块

## 模块职责
`fiber-gateway-proxy` 以脚本为核心构建可热更新的网关项目，负责创建引擎、装配脚本库、监听配置变化并将请求转发到下游。`LibProxyMainModule` 提供标准引导逻辑：创建 Engine、初始化 HTTP Server、注入脚本库与配置监听器。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L29-L185】

## 核心组件
- **LibProxyMainModule**：安装 `EventLoopGroup`、`Engine`、`DefaultHttpClient` 等基础 Bean，注册 `ProxyModule` 扩展点，并在生命周期 INIT 阶段启动 `ConfigWatcher`。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L101-L145】
- **ProxyModule/SubModule**：作为脚本项目的局部容器，绑定 `HttpLibConfigure`、`GovLibConfigure`、`RequestLibConfigure` 等脚本库，`createProjectInjector()` 用于为每个项目派生子容器。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L32-L59】
- **脚本库**：`HttpFunc` 提供 `http.*` 指令执行下游请求或透明代理；`TunnelProxy`、`TunnelProxyAuth` 支持 CONNECT 渠道；`RequestLibConfigure` 暴露 `$req` 相关常量，`GovLibConfigure` 提供限流与治理函数。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/HttpFunc.java†L29-L200】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/TunnelProxy.java†L26-L179】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/gov/GovLibConfigure.java†L1-L200】
- **ConfigWatcher**：定义配置加载接口，`LoadConfigWatcherListener` 在 INIT 时调用 `startWatch()`，由项目实现监听文件、配置中心或数据库。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/ConfigWatcher.java†L5-L10】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L101-L109】
- **路由工具**：`RoutePathMatcher`、`VarConfigSource` 等帮助解析项目 JSON 配置，将路径模板、变量注入脚本执行环境。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/route/RoutePathMatcher.java†L1-L200】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/route/VarConfigSource.java†L1-L200】

## 启动流程
1. 调用 `LibProxyMainModule.createEngine()` 组合默认模块与自定义扩展，生成 Engine 并安装 HTTP Server。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L158-L185】
2. `Engine.installExt()` 期间，`LoadConfigWatcherListener` 在 INIT 事件里启动配置监听器，将脚本项目注册到 HTTP Server。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L101-L109】
3. 配置被加载后，通过 `LibProxyMainModule.createProjectInjector()` 为每个项目生成子容器，注入脚本库、HTTP 客户端与治理能力。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L32-L59】
4. 请求到达时，脚本函数使用 `HttpFunc`/`TunnelProxy` 等能力调用下游服务或升级为双向通道，并可结合治理库执行限流、熔断。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/HttpFunc.java†L52-L200】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/TunnelProxy.java†L26-L179】

## 扩展与配置
- **自定义脚本库**：实现 `HttpLibConfigure` 并通过 `binder.bindMultiBean(HttpLibConfigure.class, …)` 注册，即可向脚本注入常量、函数或指令。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L40-L47】
- **治理能力**：`GovLibConfigure` 内置限流、熔断脚本函数，结合 `fiber-gateway-support` 的 `RateLimiter`、`CircuitBreaker` 提供可观测治理方案。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/gov/GovLibConfigure.java†L1-L200】【F:fiber-gateway-support/src/main/java/io/fiber/net/support/AtomicRateLimiter.java†L9-L190】
- **项目隔离**：每个项目拥有独立 Injector，可在配置中注入特定的下游地址、鉴权策略或度量上报，避免全局 Bean 互相干扰。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L32-L59】
- **配置监听实现**：可基于文件系统、配置中心或 DB 实现 `ConfigWatcher`，在 `startWatch()` 中解析路由、脚本并调用 HTTP Server 的 `addHandlerRouter()`。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/ConfigWatcher.java†L5-L10】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L95-L138】

## 与其他模块的协作
- 依赖 `fiber-gateway-common` 的 IoC、生命周期机制以及 `fiber-gateway-server` 的 `HttpExchange` 执行脚本逻辑。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L29-L185】【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpExchange.java†L20-L188】
- 借助 `fiber-gateway-httpclient` 完成下游请求，脚本函数直接消费 `HttpClient` 与 `ClientExchange`。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/HttpFunc.java†L29-L200】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/DefaultHttpClient.java†L11-L38】
- 与 `fiber-gateway-support` 限流熔断配合，实现治理策略在脚本中的快速落地。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/gov/GovLibConfigure.java†L1-L200】【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreakerStateMachine.java†L8-L200】
