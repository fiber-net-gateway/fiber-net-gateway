directive bd from http "http://127.0.0.1:16688";

return bd.request({
    path: "/",
    method: "POST",
    headers: {
     "X-Fiber-Project": null
    },
    includeHeaders: true,
    body: strings.repeat("12", 1024*1024)
});
