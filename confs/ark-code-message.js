directive ark = http "https://ark.cn-beijing.volces.com";

let apiKey = req.getHeader("X-Ark-Api-Key");
if (!apiKey) {
    return resp.sendJson(400, {
        error: "missing X-Ark-Api-Key"
    });
}

let result = ark.request({
    path: "/api/coding/v1/messages",
    method: "POST",
    headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + apiKey,
        "Accept": "text/event-stream",
        "anthropic-version": "2023-06-01"
    },
    body: {
        model: "ark-code-latest",
        max_tokens: 1024,
        messages: [
            {
                role: "user",
                content: "hello, who are you!!!!"
            }
        ]
    },
    includeHeaders: true
});

let contentType = result.headers["content-type"];
if (contentType) {
    resp.setHeader("Content-Type", contentType);
}

return resp.send(result.status, result.body);
