directive bd from http "http://127.0.0.1";

bd.proxyPass({
    path: "/",
    method: "GET",
    headers: {
     "X-Fiber-Project": null
    }
});
