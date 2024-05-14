package io.fiber.net.proxy;

import io.fiber.net.common.json.JsonNode;

public class UrlRoute {
    private String method;
    private String url;
    private String file;
    private JsonNode cors;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public JsonNode getCors() {
        return cors;
    }

    public void setCors(JsonNode cors) {
        this.cors = cors;
    }
}
