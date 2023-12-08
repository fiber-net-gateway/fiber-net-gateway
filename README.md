# fiber-net-gateway 
基于脚本解释器的低代码 API 网关。


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
curl 127.0.0.1:16688 -XPOST
curl 127.0.0.1:16688 -XGET
```

# 特性

## 低代码
强大的脚本解释器功能、通过脚本代替配置、简单易懂、功能强大。

## 纯异步
所有的逻辑都在netty eventloop 线程中执行，无其它业务线程。

## 轻依赖
仅仅使用了netty-http、jackson两个三方库、无其它依赖。

# 组件

## 脚本引擎
## 异步 http-client
## 异步 http-server


