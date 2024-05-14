directive fy from http "https://fanyi.baidu.com";
directive bd from http "https://www.baidu.com";
directive demoService from dubbo "com.test.dubbo.DemoService";

if (req.getMethod() == "GET") {
    let dubboResult = demoService.createUser(req.getHeader("Host"));
    resp.send(200, {dubboResult, success:true});
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
