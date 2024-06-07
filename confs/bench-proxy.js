directive bd from http "http://127.0.0.1:16688";

bd.request({
    path: "/",
    method: "GET",
    headers: {
     "X-Fiber-Project": null
    }
});
