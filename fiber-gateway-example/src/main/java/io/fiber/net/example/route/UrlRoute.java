package io.fiber.net.example.route;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fiber.net.common.json.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class UrlRoute {
    private String method;
    private String url;
    private String file;
    private JsonNode cors;

    @JsonIgnore
    final List<String> varDefinitions = new ArrayList<>();
    @JsonIgnore
    final List<Integer> varDefIdx = new ArrayList<>();

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
