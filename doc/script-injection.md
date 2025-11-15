# Script Injection Guide

[中文版本](script-injection_cn.md)

## Module Overview
`fiber-gateway-script` implements the custom JavaScript-like runtime used by the gateway. The parser (`io.fiber.net.script.parse.Parser`) understands constants whose identifiers start with `$`, resolves functions through a `Library`, and turns asynchronous calls into resumable state machines (see `AbstractVm`). By default, every script uses `StdLibrary`, but projects can extend it to publish domain-specific helpers.

## Extending StdLibrary
`StdLibrary` exposes imperative registration hooks:

- `putFunc(name, Library.Function)` registers a synchronous helper whose `call` method receives the current `ExecutionContext`.
- `putAsyncFunc(name, Library.AsyncFunction)` wires a coroutine-friendly function that must call `context.returnVal(...)` or `context.throwErr(...)` once the async work finishes.
- `putConstant(namespace, key, Library.Constant)` exposes read-only values accessed via `$namespace.key` in scripts.

When implementing async helpers, resume the script on the Netty IO thread (`Scheduler.current().schedule(...)`). The example library also falls back to a dedicated `ScheduledExecutorService` so unit tests that run outside the engine can still exercise the code path.

## Example Usage
`fiber-gateway-example/src/main/java/io/fiber/net/example/script/ScriptInjectionExample.java` packages a fully working sample:

```java
public ScriptInjectionExample() {
    ExampleScriptLibrary lib = new ExampleScriptLibrary(executor);
    Script script = Script.compile(SCRIPT_SOURCE, lib, true);
    JsonNode output = script.exec(root).await();
}
```

- `greetUser` is a synchronous function that formats a message and returns a `TextNode`.
- `delayedEcho` is an async function that validates input, schedules a resume via `Scheduler.current()` (or a fallback executor), and calls `context.returnVal(...)`.
- `$env.projectName`, `$env.releaseTag`, and `$env.allowedDelay` are constants injected with `putConstant`. Scripts read them using `$env.projectName`.

The script sample clamps request delays, invokes the synchronous/async functions, and returns a JSON object with `greeting`, `echoed`, `delayBudget`, and the injected constants. Refer to the class for the full source and the helper methods that build the root JSON payload.

## Testing & Verification
`fiber-gateway-example/src/test/java/io/fiber/net/example/script/ScriptInjectionExampleTest.java` runs on `IOThreadRunner` to mimic the Netty execution model. The tests validate:

1. Custom functions/constants are visible to the script and produce deterministic output.
2. Async helpers respect the `$env.allowedDelay` constant and resume execution after the scheduled delay.

Run `mvn -pl fiber-gateway-example -am test` to execute the suite once Maven is available in your environment.
