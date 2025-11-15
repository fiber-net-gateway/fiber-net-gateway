# fiber-gateway-common 模块

## 模块职责
`fiber-gateway-common` 提供引擎、生命周期事件、依赖注入与异步调度等基础能力，是所有服务器、代理和脚本模块的运行时核心。Engine 负责安装服务器与监听器、驱动事件循环并控制停机流程；每个 Server 实现都通过 Engine 的生命周期回调统一管理。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L15-L127】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Server.java†L5-L17】

## 关键组成
- **生命周期管理**：Engine 在 `installExt()` 中加载 `LifecycleListener`、Server，并触发 INIT/STARTED 事件，退出时依次触发 PRE_STOP/STOPPED，保证 Server 的启动与关闭顺序一致。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L29-L127】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/LifecycleListener.java†L6-L14】
- **调度模型**：`EngineScheduler` 借助 `Scheduler` 抽象封装单线程事件循环、延迟任务与固定周期任务，可切换 IO 线程或直接执行器。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L15-L127】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/async/Scheduler.java†L11-L137】
- **同步器**：`EventSyncer` 允许在安装阶段等待异步依赖（如配置、DNS）完成，只有首个成功结果到达时才恢复引擎。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/EventSyncer.java†L9-L170】
- **IoC 容器**：`Injector` 支持创建子容器或深拷贝容器树；`Binder` 则提供单例、原型、集合、多实例绑定等装配方式。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L13-L45】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Binder.java†L5-L44】
- **服务器抽象**：`AbstractServer` 统一封装路由、错误处理、拦截器链及多项目管理，是 HTTP/Proxy 等协议实现的基类。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L14-L138】

## 启动流程
1. **组合模块**：从根 Injector 派生子容器，安装需要的 Module（如 HttpServerModule、ProxyModule），再获取 Engine 实例。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L13-L39】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L23-L55】
2. **安装扩展**：调用 `engine.installExt()`，引擎会收集所有 Server、LifecycleListener 并进入事件循环，直到所有同步器完成。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L29-L55】
3. **运行主循环**：在业务线程调用 `engine.runLoop()` 阻塞等待停止信号；若需要退出可调用 `engine.signalStop()`，触发 PRE_STOP/STOPPED 并销毁容器。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/Engine.java†L72-L127】
4. **处理请求**：Server 在 `process()` 中按路由查找项目处理器，拦截器链按注册顺序执行，异常统一由 `ErrorHandler` 封装。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L38-L121】

## 常见扩展点
- **拦截器链**：通过 `Binder.bindMultiBean(Interceptor.class, …)` 注册拦截器；Engine 安装时会注入到 `AbstractServer` 的责任链。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L45-L80】
- **生命周期监听**：实现 `LifecycleListener` 可在 INIT 时加载配置、在 STOPPED 时清理资源。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/LifecycleListener.java†L6-L14】
- **异步初始化**：对耗时任务使用 `EventSyncer.syncedConsume` 或 `sync` 等方法，确保首个结果完成后再进入 STARTED 状态。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/EventSyncer.java†L154-L170】
- **自定义线程模型**：可在自定义 Module 中绑定 `EventLoopGroup` 或替换 Scheduler，使不同 Server 运行在独立线程池。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Binder.java†L16-L39】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L14-L52】

## 与其他模块的协作
- `fiber-gateway-server` 在安装时注册 HttpServer 实例，依赖 `AbstractServer` 提供的项目管理与拦截器机制。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ext/AbstractServer.java†L38-L137】
- `fiber-gateway-proxy`、`fiber-gateway-script` 等模块以 `Injector` 作为扩展容器，利用多实例绑定注入脚本库、代理策略。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L13-L45】【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Binder.java†L5-L44】
- 示例工程在 `LifecycleListener` 中结合 `EventSyncer` 同步脚本、配置更新，演示了常见的 INIT 扩展方式。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/DirectoryFilesConfigWatcher.java†L57-L131】
