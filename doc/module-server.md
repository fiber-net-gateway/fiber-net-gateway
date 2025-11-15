# fiber-gateway-server 模块

## 模块职责
`fiber-gateway-server` 基于 Netty 提供 HTTP 协议监听能力，负责暴露端口、解析请求报文、串联拦截器并分发至业务项目。核心类 `HttpServer` 继承自 `AbstractServer`，在生命周期回调中创建 `ServerBootstrap`、安装 ChannelHandler 并管理监听通道。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServer.java†L23-L148】

## 核心组件
- **HttpServer**：封装服务器实例的启动与停止逻辑，包括事件循环绑定、TCP 参数配置、监听地址选择以及默认错误处理器。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServer.java†L49-L148】
- **ReqHandler**：作为 pipeline 中的主处理器，负责 HTTP 报文解码、100-continue、body 限流、连接升级与对 Engine 的回调；当请求准备就绪后调用 `HttpServer.process()` 进入项目路由。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ReqHandler.java†L24-L200】
- **HttpExchange**：封装请求上下文，提供属性存储、监听器、body 读取、响应写入及升级接口，供脚本、代理等上层模块统一操作。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpExchange.java†L20-L188】
- **ServerConfig**：集中定义 backlog、header/body 限制、TCP 参数及默认端口，可通过系统属性或配置文件覆盖。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ServerConfig.java†L5-L137】
- **HttpServerModule**：向引擎注册 `ServerModule`、默认 Router、错误处理器以及事件循环工厂，支持运行时创建多实例 HTTP Server。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServerModule.java†L15-L82】

## 请求处理流程
1. `HttpServer.start()` 创建 `ServerBootstrap` 并绑定监听端口，同时安装 `HttpServerCodec`、`HttpServerKeepAliveHandler` 与 `ReqHandler`。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServer.java†L49-L98】
2. `ReqHandler` 在收到 `HttpRequest` 后校验管线、构造 `HttpExchangeImpl`，必要时响应 100-continue 或直接回写错误。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ReqHandler.java†L107-L200】
3. 当请求头或 body 满足触发条件时，`ReqHandler` 调用 `HttpServer.process()`，`AbstractServer` 通过 Router 查找项目并执行拦截器链。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ReqHandler.java†L170-L193】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L38-L121】
4. 响应完成后，`HttpExchange` 的监听器会依次触发 header/body 事件，支持记录指标或清理资源。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpExchange.java†L103-L188】

## 配置与调优
- **端口与多实例**：通过 `HttpServerModule.Factory` 创建命名实例，可在同一引擎内监听多个端口或隔离子容器。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServerModule.java†L24-L52】
- **连接参数**：`ServerConfig` 暴露 TCP keepalive、reuseport、最大 body 等参数，`HttpServer` 在启动时自动写入 `ChannelOption`。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServer.java†L52-L80】【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ServerConfig.java†L5-L137】
- **超大请求保护**：`ReqHandler` 基于 `ServerConfig` 的限制在读取阶段直接拒绝过大的首行、header 或 body，避免占用过多内存。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ReqHandler.java†L70-L200】【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ServerConfig.java†L5-L120】
- **协议升级**：当业务调用 `HttpExchange.upgrade()` 时，`ReqHandler.prepareUpgrade()` 会移除 keepalive 处理器并完成 Netty pipeline 升级，用于 WebSocket 等场景。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/ReqHandler.java†L170-L200】

## 常见扩展点
- 通过 `Binder.bindMultiBean(Interceptor.class, …)` 注册链路拦截器，例如统计、鉴权或流量染色。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L45-L80】
- 覆盖默认 Router，以自定义 header、Host 或 URI 规则区分项目。默认实现读取 `X-Fiber-Project` 头并映射到项目处理器。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServerModule.java†L67-L82】
- 替换 `ErrorHandler` 以输出自定义 JSON、HTML 或链路追踪信息，或将错误上报至监控系统。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpServer.java†L46-L148】

## 与其他模块的关系
- `fiber-gateway-proxy` 与 `fiber-gateway-script` 通过 `HttpExchange` 提供的 API 读取请求数据、写入响应或升级连接。 【F:fiber-gateway-server/src/main/java/io/fiber/net/server/HttpExchange.java†L20-L188】
- 示例工程借助 `HttpServerModule.Factory` 动态加载脚本项目，并结合拦截器记录指标。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L48-L94】
