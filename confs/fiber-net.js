directive fy from http "https://fanyi.baidu.com";
directive bd from http "https://www.baidu.com";
directive demoService from dubbo "com.test.dubbo.DemoService";
directive lh from http "http://127.0.0.1:16688";

resp.addCookie({name:"aaaa", value: "AAA", maxAge: 100000, "domain":".baidu.com", path: "/"});
resp.addHeader("Set-Cookie", "vvv=AAA; Expires="+time.format()+"; Path=/; Domain=.baidu.com");

if (req.getMethod() == "GET") {
    if (req.getPath() == "/metric") {
        lh.proxyPass({
            headers: {
             "X-Fiber-Project": 'metric'
            }
        });
    } else if (req.getPath() == '/favicon.ico') {
        let ico = bd.request({path: "/favicon.ico"});
        resp.setHeader("Content-Type", "image/x-icon");
        resp.send(200, ico.body);
    } else {
        resp.setHeader("Content-Type", "text/html");
        resp.send(200, "<h1>Hello, welcome to use fiber-net</h1>");
    }
} else if(req.getMethod() == "PUT") {
    let dubboResult = demoService.createUser(req.getHeader("Host"));
    resp.send(200, [{
        dubboResult,
        success:true
    },demoService.$dynamicInvoke("createUser", [req.getHeader("Host")+"222"])]);
} else if (req.getMethod() == "POST") {
    req.discardBody();
    let res = bd.request({path: "/", headers: {"User-Agent": "curl/7.88.1"}});
    resp.setHeader("Content-Type", "text/html");
    resp.send(res.status, res.body);
} else {
    fy.proxyPass({
        path: "/v2transapi",
        query: "from=en&to=zh",
        method: "POST",
        headers: {
         "X-Fiber-Project": null
        }
    });
}
