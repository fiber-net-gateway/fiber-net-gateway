# fiber-gateway-script 模块

## 模块职责
`fiber-gateway-script` 提供轻量级脚本语言运行时，支持解释执行与 AOT 编译，可在网关内以 JsonNode 为数据模型实现动态路由、流量治理和数据转换。`Script` 接口负责编译源码并输出可执行对象，支持同步与异步执行模式。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Script.java†L16-L106】

## 核心组件
- **编译入口**：`Script.compile*` / `aotCompile*` 方法基于 `Parser` 解析脚本，分别生成 `InterpreterScript` 或 `AotCompiledScript`，可控制是否允许赋值、是否开启优化。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Script.java†L18-L94】
- **执行上下文**：`ExecutionContext` 暴露根节点、附加对象、参数读取与返回/抛错接口，脚本库通过该接口与宿主（如 HttpExchange）交互。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/ExecutionContext.java†L5-L23】
- **标准库接口**：`Library` 定义常量、函数、异步函数与指令的查找机制；函数按 `FunctionCallArgs` 匹配 `FunctionSignature` 后返回 `ResolvedFunc`，供 Proxy 模块注入 HTTP、治理等扩展。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Library.java†L8-L66】
- **虚拟机实现**：`InterpreterVm` 继承 `AbstractVm`，执行编译后的字节码数组，支持同步/异步恢复、参数展开、异常捕获等逻辑。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/run/InterpreterVm.java†L13-L200】
- **异常处理**：`ScriptExecException` 继承 `FiberException`，记录出错位置与额外 JSON 元数据，方便在 HTTP 响应中返回详尽信息。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/ScriptExecException.java†L1-L83】

## 执行流程
1. 项目通过 `Script.compile()` 或 `aotCompile()` 将脚本源码转换为 `Script` 实例，可选择禁用赋值或优化以增强安全性。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Script.java†L18-L94】
2. 执行时调用 `script.exec(root, attach)`，根节点通常是 HTTP 请求封装的 JsonNode，`attach` 可传递 HttpExchange、监控上下文等对象。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Script.java†L97-L105】
3. 虚拟机读取编译后的指令，使用 `ExecutionContext` 提供的参数、返回值通道与 `Library` 提供的函数进行运算，异步函数可通过 `Maybe` 等流式接口恢复。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/run/InterpreterVm.java†L68-L200】
4. 发生错误时抛出 `ScriptExecException`，Proxy 模块可以捕获并写入统一的 JSON 错误响应。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/ScriptExecException.java†L9-L83】

## 扩展指南
- **扩展库**：实现 `Library.resolveFunc(name, FunctionCallArgs)` 或扩展 `StdLibrary`，按 `FunctionSignature` 注册同步/异步 host 函数；函数体通过 `ExecutionContext` 和 `Library.Arguments` 读取上下文与参数并返回 `JsonNode`。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Library.java†L8-L66】【F:fiber-gateway-script/src/main/java/io/fiber/net/script/std/StdLibrary.java†L24-L104】
- **异步操作**：利用 `Library.AsyncHandle.returnVal/throwErr` 与 `Maybe`/`Observable`，在回调中恢复脚本流程；`InterpreterVm` 支持在异步完成后继续执行。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Library.java†L20-L23】【F:fiber-gateway-script/src/main/java/io/fiber/net/script/run/InterpreterVm.java†L68-L200】
- **安全控制**：可通过禁用赋值、限制库函数或在编译前执行静态校验来约束脚本能力，必要时在 `ScriptExecException` 中记录错误节点实现审计。 【F:fiber-gateway-script/src/main/java/io/fiber/net/script/Script.java†L22-L94】【F:fiber-gateway-script/src/main/java/io/fiber/net/script/ScriptExecException.java†L9-L83】

## 与其他模块的协作
- Proxy 模块将 `HttpFunc`、`TunnelProxy` 等能力注册为脚本库，让脚本可直接访问 HTTP 客户端与治理工具。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/HttpFunc.java†L29-L200】【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/lib/TunnelProxy.java†L26-L179】
- Example 模块演示如何加载脚本文件、构建 `JsonNode` 请求体并在运行时刷新脚本，便于快速验证脚本逻辑。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/UrlHandlerManager.java†L13-L70】【F:fiber-gateway-example/src/main/java/io/fiber/net/example/DirectoryFilesConfigWatcher.java†L28-L131】
