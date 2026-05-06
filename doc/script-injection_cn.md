# 自定义脚本注入指南

[English version](script-injection.md)

## 模块概述
`fiber-gateway-script` 提供网关使用的自研脚本引擎。解析器（`io.fiber.net.script.parse.Parser`）会处理 `$` 开头的常量、通过 `Library` 寻找函数，并基于 `AbstractVm` 将异步调用编译为可恢复的状态机。默认情况下脚本加载 `StdLibrary`，可以在此基础上扩展自定义函数与常量。

## Host 函数 ABI
脚本中的函数是由 Java host 注入的 native 函数。当前 ABI 在解析阶段就会按调用参数匹配签名，因此 host 函数必须提供清晰的 `FunctionSignature`。

核心类型如下：

- `FunctionSignature`：描述函数名、参数列表、是否可变参数、必填参数数、默认值以及是否允许常量折叠。
- `FunctionParam.required(name)`：必填参数。
- `FunctionParam.optional(name, defaultValue)`：可选参数，默认值只能是 number、boolean、null 或 string 字面量。
- `FunctionParam.variadic(name)`：可变参数，必须放在最后。
- `FunctionCallArgs`：解析器传给 `Library.resolveFunc(name, args)` 的调用形态，包含实参个数和第一个 `...` 展开参数的位置。
- `ResolvedFunc`：`resolveFunc` 的返回值，包装匹配到的同步或异步 host 函数。

`StdLibrary` 会按签名注册和解析函数。重复注册时，如果两个签名的参数范围有重叠，会直接报冲突；调用时如果没有匹配签名，脚本编译失败；如果匹配多个签名，则报 ambiguous function call。

```java
StdLibrary lib = new StdLibrary();

lib.putFunc("sum",
        new FunctionSignature("sum", true,
                FunctionParam.required("a"),
                FunctionParam.required("b"),
                FunctionParam.variadic("rest")),
        (context, args) -> {
            int total = 0;
            for (int i = 0; i < args.getArgCnt(); i++) {
                total += args.getArgVal(i).asInt();
            }
            return IntNode.valueOf(total);
        });
```

脚本侧可以直接调用固定参数、可选参数和可变参数函数。缺省参数会在编译期补成字面量；如果调用中包含 `...` 展开参数，则不会补默认值，签名必须是可变参数，且展开位置不能出现在固定参数之前。

```javascript
sum(1, 2);
sum(1, 2, 3, 4);
sum(1, 2, ...[3, 4]);
```

同步函数实现 `Library.Function`，返回 `JsonNode`，只能抛出 `ScriptExecException`。异步函数实现 `Library.AsyncFunction`，必须通过 `Library.AsyncHandle` 恢复脚本：

```java
lib.putAsyncFunc("sleep",
        FunctionSignature.fixed("sleep", false, FunctionParam.required("millis")),
        (context, args, handle) -> {
            long millis = args.getArgVal(0).asLong();
            Scheduler.current().schedule(() -> handle.returnVal(NullNode.getInstance()), millis);
        });
```

异步函数不能声明 checked exception；异常需要通过 `handle.throwErr(...)` 返回给脚本。`constExpr=false` 的函数不会被优化器当作常量表达式折叠，适合时间、随机数、请求上下文和外部 IO 等能力。

## 扩展 StdLibrary
`StdLibrary` 暴露多个注入入口：

- `putFunc(function)`：注册自带 `FunctionSignature` 的同步函数。
- `putFunc(name, signature, function)`：按显式签名注册同步函数。
- `putFunc(name, function)`：兼容入口；如果 `function.signature()` 为 `null`，会按 `name(...args)` 注册为可变参数函数。
- `putAsyncFunc(function)`：注册自带 `FunctionSignature` 的异步函数。
- `putAsyncFunc(name, signature, function)`：按显式签名注册异步函数。
- `putAsyncFunc(name, function)`：兼容入口；如果 `function.signature()` 为 `null`，会按 `name(...args)` 注册为可变参数异步函数。
- `putConstant(namespace, key, Library.Constant)`：注册 `$namespace.key` 形式的常量。
- `putAsyncConstant(namespace, key, Library.AsyncConstant)`：注册异步常量。

实现异步函数和异步常量时，应优先使用 `Scheduler.current().schedule(...)` 在 Netty IO 线程恢复脚本。测试或非 IO 线程场景可以准备专用 executor 作为降级。

## 注解式注册
除了手写 `Library.Function`，也可以用 `ReflectLib` 扫描注解，把 public Java 方法注册成脚本函数或常量。

```java
@ScriptLib(functionPrefix = "util", namespace = "$env")
public final class EnvExports {
    @ScriptFunction(name = "join")
    public static JsonNode join(@ScriptParam("a") JsonNode a,
                                @ScriptParam(value = "items", variadic = true) JsonNode... items) {
        StringBuilder sb = new StringBuilder(a.asText());
        for (JsonNode item : items) {
            sb.append(item.asText());
        }
        return TextNode.valueOf(sb.toString());
    }

    @ScriptFunction(name = "timeout", constExpr = false, params = {
            @ScriptParam("route"),
            @ScriptParam(value = "fallback", optional = true, defaultValue = "3000")
    })
    public static JsonNode timeout(Library.Arguments args) {
        return args.getArgVal(1);
    }

    @ScriptFunction(name = "asyncEcho")
    public static void asyncEcho(Library.AsyncHandle handle,
                                 @ScriptParam("value") JsonNode value) {
        handle.returnVal(value);
    }

    @ScriptConstant(key = "project")
    public static JsonNode project() {
        return TextNode.valueOf("fiber-net-gateway");
    }
}

StdLibrary lib = new StdLibrary();
ReflectLib.registerStatic(lib, EnvExports.class);
```

注解注册的 ABI 规则：

- 函数所在类和方法必须是 `public`；静态注册只能注册 static 方法，实例注册需要传入 owner。
- 同步函数返回 `JsonNode`；异步函数返回 `void`，并在参数列表中声明 `Library.AsyncHandle`。
- 方法参数顺序必须是 `ExecutionContext`、`Library.AsyncHandle`、脚本参数。`ExecutionContext` 和 `AsyncHandle` 都是可选的，但如果出现，必须在所有脚本参数之前。
- 脚本参数可以是带 `@ScriptParam` 的 `JsonNode`、最后一个 `JsonNode[]` 可变参数，或者唯一且最后一个 `Library.Arguments`。
- `Library.Arguments` 形式需要在 `@ScriptFunction(params = {...})` 上显式声明签名，否则会被视为 `(...args)`。
- `@ScriptParam(optional = true, defaultValue = "...")` 声明默认值；`@ScriptParam(variadic = true)` 只能用于最后一个 `JsonNode[]`。
- `@ScriptLib(functionPrefix = "util")` 会把 `join` 注册为 `util.join`；`@ScriptLib(namespace = "$env")` 为常量提供默认命名空间。

## 示例
```java
String source = ""
        + "let timeout = util.timeout('home');"
        + "let msg = util.join($env.project, ':', timeout);"
        + "return util.asyncEcho(msg);";

Script script = Script.compile(source, lib, true);
JsonNode output = script.exec(root).await();
```

## 测试与验证
建议至少覆盖以下测试：

1. 自定义函数、可选参数、可变参数和常量能被脚本正确解析。
2. 参数不匹配、重叠签名、非法默认值会在编译或注册阶段失败。
3. 异步函数和异步常量能通过 `AsyncHandle` 恢复脚本。
4. 解释器模式和 AOT 模式返回一致结果。

本地安装 Maven 后，可运行 `mvn -pl fiber-gateway-script test` 执行脚本模块测试；涉及 HTTP 注入时再运行对应 proxy/example 模块测试。
