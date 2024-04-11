# fiber-net-gateway 
一个基于脚本引擎的低代码 API 网关 | FaaS 框架。实现的功能是每收到一次 http 请求，执行一段脚本。

用于协议转换、反向代理、服务编排，[BFF](https://zhuanlan.zhihu.com/p/634498512)等。

性能非常高，是世界上最快的 java 网关｜FaaS 框架了（没有之一）。
- http 处理是全异步的，没有用到 线程池，除了 Netty 的 IO 线程外无其它线程。
- 脚本的执行是通过协程实现的，是的，用 java 8 也能跑出协程来。[原理说明](doc/script.md)
- 脚本有两种执行方式：解释器（Interpreter）方式 使用的是 stackful 协程（类似 lua 解释器）；
AOT 方式使用的是 stackless 协程（类似 rust），脚本会被编译为状态机 class。

ps：仅仅是一个框架，若要用于生产环境，需要进行二次开发，比如接入配置中心，扩展脚本函数等。


# 使用

- 编译
```bash
git clone https://github.com/fiber-net-gateway/fiber-net-gateway.git
cd fiber-net-gateway
mvn package -DskipTests
cd fiber-gateway-example/target
```

- 创建配置文件
```bash
mkdir conf
cat > conf/fiber-net.gs << EOF
directive fy from http "https://fanyi.baidu.com";
directive bd from http "https://www.baidu.com";
directive demoService from dubbo "com.test.dubbo.DemoService";

if (req.getMethod() == "GET") {
    let dubboResult = demoService.createUser(req.getHeader("Host"));
    resp.send(200, {dubboResult, success:true});
} else if (req.getMethod() == "POST") {
    fy.proxyPass({
        path: "/v2transapi",
        query: "from=en&to=zh",
        method: "POST",
        headers: {
            "X-Fiber-Project": null
        }
    });
} else {
      req.discardBody();
      let res = bd.request({path: "/", headers: {"User-Agent": "curl/7.88.1"}});
      resp.setHeader("Content-Type", "text/html");
      resp.send(res.status, res.body);
}
EOF
```

- 运行
```bash
java -jar -Dfiber.dubbo.registry=nacos://<nacos_ip_port> fiber-gateway-example-1.0-SNAPSHOT.jar conf
```

- 测试：每发一个请求，上边的脚本就会执行一次
```bash
# 使用 get 请求，返回 dubbo DemoService.createUser 调用的结果
curl 127.0.0.1:16688 -XGET
# 使用 post 请求，反向代理到百度翻译 （反向代理会透传 downstream 内容）
curl 127.0.0.1:16688 -XPOST
# 使用其它请求，返回百度主页。（请求模式，不会透传 downstream）。
curl 127.0.0.1:16688 -XPUT
```
dubbo 定义如下
```java
package com.test.dubbo;
public interface DemoService {
    User createUser(String name);
    class User {
        private String name;
        private int age;
        private boolean male;
        // getter/setter ...
    }
}
```
每次请求，fiber-net.gs 都会被执行一次。也可以在 conf 目录下放置其它 .gs 文件
通过 request header "X-Fiber-Project" 执行被执行的文件名。（如 ttt.gs）
```bash
### conf/ttt.gs 文件会被执行，不指定则执行 fiber-net.gs 
curl 127.0.0.1:16688 -H'X-Fiber-Project: ttt'
```
详细说明请参考 [使用文档](doc/user.md)

扩展二次开发请参考 [开发文档](doc/dev.md)

