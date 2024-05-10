# fiber-net-gateway 
一个基于脚本引擎的低代码 API 网关 | FaaS 框架。实现的功能是每收到一次 http 请求，执行一段脚本。

在脚本里，可以写一些调用http，dubbo 或其它简单逻辑，即可以实现协议转换、反向代理、服务编排，[BFF](https://zhuanlan.zhihu.com/p/634498512)等功能。

本项目包含4个基本组件:
- 基于 netty 的异步 http server。
- 基于 netty 的异步 http client，支持 https DNS 连接池。
- dubbo client 封装（nacos/dubbo3/泛化调用）。
- 同时支持解释器和 AOT 的脚本引擎。

性能非常高，是世界上最快的 java 网关｜FaaS 框架了（没有之一）。有如下亮点：
- 专门为 FaaS | API 网关等场景设计的脚本语言，区别于其它网关复杂难以理解的配置，简单易懂。
- 所有组件全是异步的，没有用到 线程池，除了 Netty 的 IO 线程外无其它线程。

脚本有两种执行方式，并且都支持协程（[原理说明](doc/script.md)）：
- 解释器（Interpreter）方式：把脚本编译为一串类似汇编的指令，在 java 中模拟指令的运行，类似 lua 解释器；
- AOT（Ahead of Time Compilation）方式：会把脚本编译为一个 java class，由 JVM 直接运行，像 rust 一样通过状态机编译来支持协程。

ps：仅仅是一个框架，若要用于生产环境，需要进行二次开发，比如接入配置中心，扩展脚本函数等。


# 使用

- 编译
```bash
git clone https://github.com/fiber-net-gateway/fiber-net-gateway.git
cd fiber-net-gateway
mvn package -DskipTests
cd fiber-gateway-example/target
```

- 创建脚本文件，脚本内容是对数组求和
```bash
mkdir scripts
cat > scripts/fiber-net.js << EOF
let bodyArray = req.readJson();
let sum = 0;
for (let _, value of bodyArray) {
    sum = sum + value;
}
return "sum = " + sum;
EOF
```

- 运行
```bash
java -jar fiber-gateway-example-1.0-SNAPSHOT.jar scripts
```

- 测试：每发一个请求，上边的脚本就会执行一次
```bash
# 调用服务对数组求和
curl 127.0.0.1:16688 -XPOST -H'Content-Type: application/json' -d'[1,2,3,4]' -i
# ======================================================
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
date: Fri, 10 May 2024 08:28:01 GMT
server: fiber-net(fn)/dev/dev
transfer-encoding: chunked

"sum = 10"
```

详细说明请参考 [使用文档](doc/user.md)

扩展二次开发请参考 [开发文档](doc/dev.md)

