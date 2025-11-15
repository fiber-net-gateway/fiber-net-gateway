# 注入器说明

[English version](injector.md)

## 概览
依赖注入工具位于 `fiber-gateway-common` 的 `io.fiber.net.common.ioc` 包下。`Injector` 是一个轻量容器，负责解析由 `Module` 描述的绑定。入口是 `Injector.getRoot()`，它表示一个空容器。通常会组合若干模块并调用 `createChild`，从而基于 `BeanDefinationRegistry` 得到一个真正可用的 `InjectorImpl`。

## Bean 解析
模块通过 `Binder` 提供的 `bind`、`bindFactory`、`bindPrototype`、`bindMultiBean` 等方法注册 Bean。当调用 `injector.getInstance(SomeType.class)` 时，注入器会在当前注册表中查找，如果未命中则回退到父级。工厂（`bindFactory`）在每个注入器内只创建一次，并支持生命周期回调：实现 `Initializable` 的对象会自动执行 `init()`，实现 `Destroyable` 的对象会被记录，随后在 `Injector.destroy()` 时按照 LIFO 顺序销毁。`getInstances` 会根据绑定时的 `order` 聚合多 Bean（类、对象或工厂）。

## 子注入器与 Fork
`Injector.createChild(modules)` 会在当前注入器之上新增绑定，新子级继承父级的所有 Bean，同时可以添加请求级原型（例如 `RequestContext`）。`Injector.fork()` 会复制一份拥有相同父级与注册表的注入器，但拥有独立的单例缓存，非常适合预热或多租户场景。`deepFork(Predicate)` 会沿着父链不断向上，直到谓词返回 false，实现选择性复制。

## 生命周期语义
只有由 `bindFactory` 产出的对象会被跟踪并在 `destroy()` 时触发 `Destroyable.destroy()`。由于难以追踪实例，`bindPrototype` 不允许绑定实现 `Destroyable` 的类型。需要为每个短生命周期注入器显式调用 `destroy()`，以确保连接池、缓冲区等资源能够及时释放。

## 示例
在 `fiber-gateway-example/src/main/java/io/fiber/net/example/ioc/InjectorUsageExample.java` 中可以看到完整 API：
- 根注入器 → 引擎 → 项目 → 请求级注入器层层构建；
- 通过 `Injector.fork()` 进行预热，避免污染主注入器中的单例；
- `getInstance` 获取诸如 `GreetingWorkflow` 等单例，`getInstances` 汇聚 `bindMultiBean` 注册的多个 `GreetingHandler`；
- `LifecycleAwareResource` 实现 `Destroyable`，由工厂创建并且在注入器销毁时自动清理。

运行 `mvn -pl fiber-gateway-example -am test` 可查看示例和 `InjectorUsageExampleTest` 中的断言，了解父子注入器、请求级子注入器以及 `Destroyable` 生命周期的具体表现。
