# fiber-net-gateway 
一个基于脚本解释器的易扩展的低代码 API 网关。


# 使用

- 编译
```
git clone https://github.com/fiber-net-gateway/fiber-net-gateway.git
cd fiber-net-gateway
mvn package -DskipTests
cd fiber-gateway-proxy/target
```

- 创建配置文件
```
mkdir conf
cat > conf/fiber-net.js << EOF
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
```
java -jar fiber-gateway-proxy-1.0-SNAPSHOT.jar conf
```

- 测试
```
# 使用 post 请求，反向代理到百度翻译 （反向代理会透传 downstream 内容）
curl 127.0.0.1:16688 -XPOST
# 使用其它请求，返回百度主页。（请求模式，不会透传 downstream）。
curl 127.0.0.1:16688 -XGET
```

# 特性

## 低代码
强大的脚本解释器功能，通过脚本代替配置，可以实现动态的业务逻辑。
脚本支持热更新，功能强大、简单易懂、易扩展。

## 纯异步
基于 netty 实现的纯异步的 http server 和 http client，使用堆外内存、零拷贝。
支持异步连接池、可以方便的实现反向代理等功能。
所有的逻辑都在 eventloop 线程中执行，无其它业务线程。

## 轻依赖
仅仅使用了 netty-http、jackson 两个三方库、无其它依赖。整个项目代码仅 14000 行，短小精悍。



