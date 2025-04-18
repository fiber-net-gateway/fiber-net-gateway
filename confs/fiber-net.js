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
} else if("/" != req.getPath()) {
    return bd.proxyPass({
        headers: {
            "X-Fiber-Project": null,
        }
    });
}

let result = {};

let bin = length(req.readBinary());
result.rawBody = strings.toString(bin);
result.path = req.getPath();
result.reqHeader = req.getHeader();
result.query = req.getQueryStr();

return result;
