# fiber-net-gateway 
一个基于脚本解释器的低代码 API 网关。用于协议转换、反向代理、服务编排，[BFF](https://zhuanlan.zhihu.com/p/634498512)等。

此项目是由 阿里本地生活 使用的 API 网关改进而来，在内部有非常广泛的使用，在本地生活承担 40W QPS，支持 http 、dubbo。

本项目的亮点是可以通过脚本来代替复杂的网关配置，灵活性高，使用简单直观（毕竟 API 网关的用户都是程序员）。


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
if(req.getMethod() == "POST") {
    fy.proxyPass({
        path: "/v2transapi",
        query: "from=en&to=zh",
        method: "POST",
        headers: {
            "X-Fiber-Project": null
        }
    });
} else {
    let res = bd.request({path: "/"});
    resp.setHeader("Content-Type", "text/html");
    resp.send(res.status, res.body);
}
EOF
```

- 运行
```bash
java -jar fiber-gateway-example-1.0-SNAPSHOT.jar conf
```

- 测试
```bash
# 使用 post 请求，反向代理到百度翻译 （反向代理会透传 downstream 内容）
curl 127.0.0.1:16688 -XPOST
# 使用其它请求，返回百度主页。（请求模式，不会透传 downstream）。
curl 127.0.0.1:16688 -XGET
```
每次请求，fiber-net.gs 都会被执行一次。也可以在 conf 目录下放置其它 .gs 文件
通过 request header "X-Fiber-Project" 执行被执行的文件名。（如 ttt.gs）
```bash
### conf/ttt.gs 文件会被执行，不指定则执行 fiber-net.gs 
curl 127.0.0.1:16688 -H'X-Fiber-Project: ttt'
```
详细说明请参考 [使用文档](doc/user.md)

扩展二次开发请参考 [开发文档](doc/dev.md)


# 说明
本项目的核心是一个内置轻量级脚本解释器，.gs 文件经过 分词（lex）、解析（parse）、优化（optimize）、编译（compile）之后被解释执行。
每次请求会有 一个协程（coroutine, fiber thread）解释执行 .gs 脚本， 起名 “fiber-gateway” 含义正是来自于此。

除了 脚本解释器 之外 本项目还实现了纯异步的 http server 和 http client， 三者结合就可以作为一个低代码 API 网关使用。

# 特性
炸裂的性能、支持协程、纯异步、零拷贝。
