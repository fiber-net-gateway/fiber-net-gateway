# 脚本引擎的设计
本文详细讲述 脚本引擎协程的实现原理。

## 解释器
例如我有这样一个脚本
```javascript
let a = 10;
let b = 20;
print(a + b);
sleep(b); // sleep 函数阻塞
print(a);
```
中间有一个 sleep 函数。
这个段脚本会被编译为几个指令
```asm
const 10
store 0;
const 20;
store 1 20;
load 0;
load 1;
add;
call print;
pop;
load 1;
call sleep;
pop;
load 0;
call print;
pop;
return;
```
敬请期待....


## AOT 动态编译
把这个脚本 转换为 一个 class 文件，动态加载为 class 来执行。性能 大概是解释器模式的 10 倍。

敬请期待....

## 协程

在 脚本执行过程中，常常使用一些耗时函数。比如 ```req.readJson();``` 调用等。
它的执行需要 等待 客户端此传来完整的 body 之后，把它解析为一个 json 然后返回。
这是一个 耗时的操作，如果使用让 java 线程阻塞等待，则需要消耗大量的线程。
所以需要使用到协程（coroutine）技术来解决这个问题。

所谓协程，就是通过异步来模拟同步，起到减少操作系统线程的目的。

以上文的 脚本为例。在 java 中 sleep 函数如下实现如下。
```java
putAsyncFunc(
    "sleep", 
    context -> {
        int timeout = context.noArgs() ? 0 : context.getArgVal(0).asInt(3000);
        // 定时器 通知
        Scheduler.current().schedule(() -> context.returnVal(null), timeout);
        return;
    }
);
```
通过定时器来定时，当时间到后 `context.returnVal(null);` 来通知脚本继续往下运行。

### 解释器模式的携程
解释器的栈和变量表都是在 java 代码中通过数组来模拟的。
所以当调用异步函数的时候，直接退出，当 context.returnVal(null); 调用，继续往下执行。

### AOT 模式的协程
AOT 模式下， 脚本会被转成 一个 class 文件。
并没有像 JDK21 的virtual thread 或者 golang 一样 保存 cpu 堆栈 和 寄存器。
而是 像 rust 一样，把 sleep 前后的代码 编译为两段，通过状态机控制执行 哪一段。
其中跨越异步函数的变量（如上文的变量 b），会被转变为 field。java 字节码大概是这个样子。
```java
protected void run() throws ScriptExecException {
    switch (this.asyncState) {
        case 0:
            this._async_var_0 = _LITERAL_0; // 变量 a
            ValueNode _local_1 = _LITERAL_1; // 变量 b
            this._stack_0 = Binaries.plus(this._async_var_0, _local_1);
            this.funcParamSP = 0;
            this.funcArgc = 1;
            this.spread = false;
            _FUNC_0.call(this); // 调用 print 函数
            this._stack_0 = _local_1;
            this.funcParamSP = 0;
            this.funcArgc = 1;
            this.spread = false;
            if (this.callAsyncFunc(_ASYNC_FUNC_0)) { // 调用 sleep 函数。sleep 函数是异步
                this.asyncState = 1; // 异步函数没有返回，把状态机设置为1，退出 run 函数。
                return;
            } else {
                this.state = 1;
            }
        case 1: // 等到 context.returnVal(); 执行的时候，run 函数再次被运行。由于上次退出状态机变为1.所以这里直接在此运行
            if (this.rtError != null) {
                throw this.rtError;
            }

            this._stack_0 = this._async_var_0; // 变量 a 是一个跨越 异步函数的变量，所以变成 field
            this.funcParamSP = 0;
            this.funcArgc = 1;
            this.spread = false;
            _FUNC_0.call(this); // 调用 print 函数
            this.rtValue = null;
            this.state = 7; // 设置退出状态， 退出
            return;
        default:
            throw new IllegalStateException("[BUG] not hit asyncState");
    }
}
```

