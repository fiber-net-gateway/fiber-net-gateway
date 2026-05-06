directive bd = http "http://127.0.0.1:8080";

if (req.getHeader("Proxy-Connection")) {
    let h = req.getHeader("Proxy-Authorization");
    if (!h) {
        return req.tunnelProxyAuth();
    }
    if(strings.hasPrefix(h, "Basic ")){
        h = strings.toString(binary.base64Decode(strings.substring(h, 6)));
    }
    if(!strings.hasPrefix(h, "admin:")){
        return resp.sendJson(401, h);
    }
    return req.tunnelProxy();
}

if ("/ws" == req.getPath()) {
    return bd.proxyPass({
        headers: {
            "X-Fiber-Project": null,
        },
        "websocket": 50000
    });
}

let result = {};

let bin = length(req.readBinary());
result.rawBody = strings.toString(bin);
result.path = req.getPath();
result.reqHeader = req.getHeader();
result.query = req.getQueryStr();
let bodyArray = [1,2,3,4];
directive baidu = http "https://www.baidu.com";
let sum = 0;
for (let _, value of bodyArray) {
    sum = sum + value;
}
result.rr = `tt == ${baidu.request({path: "/favicon.ico"}).body}`;
result.tt = `sum = ${sum}`;

return result;
