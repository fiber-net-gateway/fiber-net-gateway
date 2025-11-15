# 自定义脚本注入指南

[English version](script-injection.md)

## 模块概述
`fiber-gateway-script` 提供网关使用的自研脚本引擎。解析器（`io.fiber.net.script.parse.Parser`）会处理 `$` 开头的常量、通过 `Library` 寻找函数，并基于 `AbstractVm` 将异步调用编译为可恢复的状态机。默认情况下脚本加载 `StdLibrary`，可以在此基础上扩展自定义函数与常量。

## 扩展 StdLibrary
`StdLibrary` 暴露多个注入入口：

- `putFunc(name, Library.Function)`：注册同步函数，在 `call` 中通过 `ExecutionContext` 访问参数。
- `putAsyncFunc(name, Library.AsyncFunction)`：注册协程友好的异步函数，需要在异步任务完成后调用 `context.returnVal(...)` 或 `context.throwErr(...)`。
- `putConstant(namespace, key, Library.Constant)`：注册 `$namespace.key` 形式的常量。

实现异步函数时，应优先使用 `Scheduler.current().schedule(...)` 在 Netty IO 线程恢复脚本。示例库同时准备了一个 `ScheduledExecutorService` 作为降级，便于单元测试或非 IO 线程场景。

## 示例
`fiber-gateway-example/src/main/java/io/fiber/net/example/script/ScriptInjectionExample.java` 包含完整示例：

```java
ExampleScriptLibrary lib = new ExampleScriptLibrary(executor);
Script script = Script.compile(SCRIPT_SOURCE, lib, true);
JsonNode output = script.exec(root).await();
```

- `greetUser` 为同步函数，返回问候语。
- `delayedEcho` 为异步函数，检查参数后调度定时任务，在回调中调用 `context.returnVal(...)`。
- `$env.projectName`、`$env.releaseTag`、`$env.allowedDelay` 通过 `putConstant` 注入，脚本通过 `$env.projectName` 等方式读取。

脚本逻辑会限制最大延迟、调用上述函数，并返回包含 `greeting`、`echoed`、`delayBudget` 以及注入常量的 JSON 对象。源码中还包含构造脚本入参（root JSON）的辅助方法。

## 测试与验证
`fiber-gateway-example/src/test/java/io/fiber/net/example/script/ScriptInjectionExampleTest.java` 依赖 `IOThreadRunner`，模拟 Netty 执行环境并验证：

1. 自定义函数和常量能被脚本正确解析。
2. 异步函数会遵守 `$env.allowedDelay` 常量并在延迟后恢复脚本。

本地安装 Maven 后，可运行 `mvn -pl fiber-gateway-example -am test` 执行该测试套件。
