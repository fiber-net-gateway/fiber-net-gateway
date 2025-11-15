# Injector 使用指南

`io.fiber.net.common.ioc.Injector` 是 fiber 网关的轻量级依赖注入容器，负责装配 `Module` 暴露的 Bean 并提供层级化的生命周期管理。本指南介绍容器的创建、Bean 获取与销毁流程，并结合示例工程中的实际代码展示最佳实践。

## 容器创建与模块安装
- **根容器**：通过 `Injector.getRoot()` 获取全局根容器，它仅保存基础配置，不直接承载业务 Bean。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L13-L17】
- **子容器**：调用 `createChild(Collection<Module>)` 为一组模块创建子容器。容器会把父级注册表与新模块合并后返回新的 `Injector` 实例。若模块集合为空会抛出 `IllegalArgumentException`，请在装配前完成校验。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L23-L38】
- **模块安装顺序**：`Module.order()` 越小越先执行，可以在模块实现中覆写次序确保依赖模块优先注册。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Module.java†L3-L9】

在示例模块中，我们通过 `Injector.getRoot().createChild(Collections.singletonList(new InjectorExampleModule()))` 即可完成模块装载。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/injector/InjectorUsageExample.java†L20-L28】

## Bean 注册与获取
- 在模块的 `install` 方法中使用 `Binder` 提供的绑定 API 将实现注册到容器，例如 `bindFactory`、`bindPrototype`、`bindMultiBean` 等。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Binder.java†L5-L42】
- 通过 `injector.getInstance(SomeClass.class)` 获取单例 Bean；如需同类型的全部实现，可使用 `getInstances(Class)`。容器内部会根据注册表构造或返回缓存实例。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L40-L45】

示例模块 `InjectorExampleModule` 使用 `bindFactory` 注册 `GreetingService` 与 `GreetingFacade`，后者依赖前者并由容器自动注入： 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/injector/InjectorExampleModule.java†L18-L33】

```java
Injector injector = InjectorUsageExample.createGreetingInjector();
GreetingFacade facade = injector.getInstance(GreetingFacade.class);
String message = facade.greet("Fiber"); // => "Hello, Fiber!"
```

## 容器 Fork 与层级隔离
若需要针对不同项目生成隔离环境，可以调用 `fork()` 或 `deepFork(Predicate<Injector>)` 复制当前容器树，避免 Bean 定义互相污染。Fork 后的容器仍可通过 `getParent()` 回溯父级链路。 【F:fiber-gateway-common/src/main/java/io/fiber/net/common/ioc/Injector.java†L18-L22】

## 容器销毁
业务完成后调用 `injector.destroy()` 触发 Bean 的销毁回调并释放资源。示例工程在单元测试中于 `tearDown` 阶段主动销毁容器，确保线程池、事件循环等资源被释放： 【F:fiber-gateway-example/src/test/java/io/fiber/net/example/injector/InjectorUsageTest.java†L21-L33】

> **提示**：当容器创建失败或模块安装过程中出现异常时，应及时调用 `destroy()` 回滚。参考 `LibProxyMainModule` 在引擎安装失败时的清理逻辑。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/LibProxyMainModule.java†L181-L209】

通过以上步骤，即可在任何业务模块中快速构建隔离且可复用的依赖注入环境。
