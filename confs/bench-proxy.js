directive bd = http "http://127.0.0.1:16688";
directive ws = http "http://127.0.0.1:8080";

if ("/ws" == req.getPath()) {
    return ws.proxyPass({
        path: "/",
        method: "POST",
        headers: {
            "X-Fiber-Project": null,
        },
        "websocket": 50000
    });
} else {
    return bd.proxyPass({
        path: "/",
        method: "POST",
        headers: {
            "X-Fiber-Project": "big-body"
        },
        includeHeaders: true,
        body: strings.repeat("12", 1024*1024)
    });
}

