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
