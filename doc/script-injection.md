# Script Injection Guide

[中文版本](script-injection_cn.md)

## Module Overview
`fiber-gateway-script` implements the custom JavaScript-like runtime used by the gateway. The parser (`io.fiber.net.script.parse.Parser`) understands constants whose identifiers start with `$`, resolves functions through a `Library`, and turns asynchronous calls into resumable state machines (see `AbstractVm`). By default, every script uses `StdLibrary`, but projects can extend it to publish domain-specific helpers.

## Host Function ABI
Script functions are native helpers injected by the Java host. The current ABI resolves functions during parsing by matching the call shape against a `FunctionSignature`, so host functions should expose explicit signatures.

Core types:

- `FunctionSignature`: describes the function name, parameter list, variadic flag, required count, default values, and whether the call may be constant-folded.
- `FunctionParam.required(name)`: required parameter.
- `FunctionParam.optional(name, defaultValue)`: optional parameter. Defaults must be number, boolean, null, or string literals.
- `FunctionParam.variadic(name)`: variadic parameter. It must be the last parameter.
- `FunctionCallArgs`: passed to `Library.resolveFunc(name, args)` by the parser. It contains the argument count and the first `...` spread argument index.
- `ResolvedFunc`: the matched sync or async function returned by `resolveFunc`.

`StdLibrary` registers and resolves functions by signature. Registration rejects overlapping signatures. Compilation fails when no signature matches a call, and ambiguous matches are reported as ambiguous function calls.

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

Scripts can call fixed, optional, and variadic helpers. Missing optional arguments are filled as literals at compile time. If a call contains `...`, defaults are not filled; the matched signature must be variadic, and the spread position cannot appear before fixed parameters.

```javascript
sum(1, 2);
sum(1, 2, 3, 4);
sum(1, 2, ...[3, 4]);
```

Synchronous functions implement `Library.Function`, return `JsonNode`, and may only throw `ScriptExecException`. Async functions implement `Library.AsyncFunction` and must resume the script through `Library.AsyncHandle`:

```java
lib.putAsyncFunc("sleep",
        FunctionSignature.fixed("sleep", false, FunctionParam.required("millis")),
        (context, args, handle) -> {
            long millis = args.getArgVal(0).asLong();
            Scheduler.current().schedule(() -> handle.returnVal(NullNode.getInstance()), millis);
        });
```

Async functions cannot declare checked exceptions; report failures through `handle.throwErr(...)`. Use `constExpr=false` for functions that depend on time, randomness, request context, or external IO so the optimizer does not constant-fold them.

## Extending StdLibrary
`StdLibrary` exposes imperative registration hooks:

- `putFunc(function)` registers a sync helper with its own `FunctionSignature`.
- `putFunc(name, signature, function)` registers a sync helper with an explicit signature.
- `putFunc(name, function)` is the compatibility hook. If `function.signature()` is `null`, it registers `name(...args)`.
- `putAsyncFunc(function)` registers an async helper with its own `FunctionSignature`.
- `putAsyncFunc(name, signature, function)` registers an async helper with an explicit signature.
- `putAsyncFunc(name, function)` is the compatibility hook. If `function.signature()` is `null`, it registers `name(...args)`.
- `putConstant(namespace, key, Library.Constant)` exposes read-only values accessed via `$namespace.key` in scripts.
- `putAsyncConstant(namespace, key, Library.AsyncConstant)` exposes async constants.

When implementing async helpers and async constants, resume the script on the Netty IO thread (`Scheduler.current().schedule(...)`). Tests or non-IO-thread callers can use a dedicated executor as a fallback.

## Annotation Registration
Instead of implementing `Library.Function` directly, `ReflectLib` can scan annotations and expose public Java methods as script functions or constants.

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

Annotation ABI rules:

- The declaring class and exported methods must be `public`; static registration only accepts static methods, while instance registration requires an owner object.
- Sync functions return `JsonNode`; async functions return `void` and declare `Library.AsyncHandle`.
- Parameter order must be `ExecutionContext`, `Library.AsyncHandle`, then script parameters. `ExecutionContext` and `AsyncHandle` are optional, but if present they must come before every script parameter.
- Script parameters may be `JsonNode` with `@ScriptParam`, a final variadic `JsonNode[]`, or one final `Library.Arguments`.
- `Library.Arguments` requires an explicit `@ScriptFunction(params = {...})` signature; otherwise the function is treated as `(...args)`.
- `@ScriptParam(optional = true, defaultValue = "...")` declares a default value. `@ScriptParam(variadic = true)` is valid only for the last `JsonNode[]`.
- `@ScriptLib(functionPrefix = "util")` registers `join` as `util.join`; `@ScriptLib(namespace = "$env")` provides a default namespace for constants.

## Example Usage
```java
String source = ""
        + "let timeout = util.timeout('home');"
        + "let msg = util.join($env.project, ':', timeout);"
        + "return util.asyncEcho(msg);";

Script script = Script.compile(source, lib, true);
JsonNode output = script.exec(root).await();
```

## Testing & Verification
Cover at least these cases:

1. Custom functions, optional parameters, variadic parameters, and constants are visible to scripts.
2. Argument mismatches, overlapping signatures, and invalid default values fail during compilation or registration.
3. Async functions and async constants resume through `AsyncHandle`.
4. Interpreter and AOT execution return the same results.

Run `mvn -pl fiber-gateway-script test` for script module coverage. Run the proxy/example module tests as well when the injected functions touch HTTP behavior.
