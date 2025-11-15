# fiber-gateway-httpclient 模块

## 模块职责
`fiber-gateway-httpclient` 提供面向网关内部的高性能 HTTP 客户端，封装连接池、DNS 解析、请求构造与响应流消费，常用于脚本或代理模块发起下游调用。核心入口 `HttpClient` 暴露 `refer()` 方法按 host/port 创建一次性请求上下文。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/HttpClient.java†L3-L8】

## 核心组件
- **DefaultHttpClient**：默认实现负责创建 `ConnectionPool`、初始化 Netty 事件循环并在 `init()` 时阻塞等待连接池预热。调用 `refer()` 会生成新的 `ClientExchange`。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/DefaultHttpClient.java†L11-L52】
- **ConnectionPool**：按事件循环线程维护连接持有者，支持线程亲和、跨线程借用以及空闲驱逐，`getConn()` 会在 IO 线程或提交任务中获取/创建连接。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/ConnectionPool.java†L16-L200】
- **ClientExchange**：封装单次请求的所有参数（方法、URI、header、body、超时等）并提供响应观察者，允许注册连接事件监听器与异步 body 流。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/ClientExchange.java†L22-L200】
- **PoolConfig**：集中配置最大空闲连接、跨线程借用、请求/连接超时、User-Agent 与 SSL 信任策略，默认为 InsecureTrustManager 便于测试。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/PoolConfig.java†L11-L174】
- **ConnectionFactoryImpl**：负责 DNS 解析、选择 Epoll/NIO Channel、设置 TCP 选项并发起连接，是连接池实际的创建器。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/util/ConnectionFactoryImpl.java†L24-L129】

## 请求生命周期
1. 业务调用 `HttpClient.refer()` 获得 `ClientExchange`，配置 method、header、body 及超时参数。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/DefaultHttpClient.java†L30-L38】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/ClientExchange.java†L124-L200】
2. 调用 `ClientExchange` 的 `observe()`（脚本中由库封装）时，连接池会在当前或目标 IO 线程上查找可复用连接，不足时通过 `ConnectionFactory` 创建新连接。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/ConnectionPool.java†L93-L200】
3. 连接建立后，`HttpConnectionHandler` 负责写入请求并按配置触发响应流，`ClientExchange` 的属性和监听器用于保存上下文数据或处理异常。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/ConnectionPool.java†L117-L200】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/ClientExchange.java†L115-L200】
4. 完成或失败后连接回收到当前线程的 `ThreadConnHolder`，在超过 `maxRequestPerConn` 或空闲超时后被自动关闭。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/ConnectionPool.java†L32-L115】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/PoolConfig.java†L60-L149】

## 配置要点
- **事件循环**：构造 `DefaultHttpClient` 时需传入与服务器不同的 `EventLoopGroup`，可通过 `LibProxyMainModule` 统一创建。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/DefaultHttpClient.java†L14-L24】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L111-L140】
- **超时策略**：`ClientExchange` 支持分别设置连接、请求与升级超时，结合 `PoolConfig` 的全局默认值实现细粒度控制。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/ClientExchange.java†L124-L200】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/PoolConfig.java†L28-L164】
- **跨线程复用**：当 `PoolConfig.useCrossConnection` 为 true 时，连接池会遍历其它事件循环的持有者寻找可复用连接，减少建连开销。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/ConnectionPool.java†L109-L168】【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/impl/PoolConfig.java†L70-L149】
- **DNS 解析**：`ConnectionFactoryImpl` 根据配置启用 DnsCache/CNameCache，并在解析失败时抛出 `HttpDnsException`，可结合脚本处理回退逻辑。 【F:fiber-gateway-httpclient/src/main/java/io/fiber/net/http/util/ConnectionFactoryImpl.java†L46-L118】

## 与其他模块的协作
- Proxy 模块中的 `HttpFunc`、`TunnelProxy` 等脚本库通过注入的 `HttpClient` 发起下游请求，实现同步/异步代理能力。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L62-L99】
- 示例工程中自定义脚本函数可直接获取 `ClientExchange`，实现 A/B 测试、熔断等高级策略。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/Main.java†L48-L94】
