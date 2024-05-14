package io.fiber.net.proxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fiber.net.common.HttpMethod;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class CorsConfig {
    private static final Set<HttpMethod> DEF_ALLOW_METHODS = EnumSet.of(HttpMethod.GET,
            HttpMethod.POST, HttpMethod.OPTIONS, HttpMethod.DELETE, HttpMethod.TRACE);
    private static final Set<String> DEF_ALLOW_HEADERS = Collections.emptySet();
    private static final Set<String> DEF_ALLOW_EXPOSE = Collections.emptySet();
    private static final Set<String> DEF_ALLOW_ORIGIN;

    static {
        DEF_ALLOW_ORIGIN = Collections.singleton("*");
    }

    private boolean enable;
    @JsonProperty("allow_origin")
    private Set<String> allowOrigin = DEF_ALLOW_ORIGIN;
    @JsonProperty("allow_methods")
    private Set<HttpMethod> allowMethods = DEF_ALLOW_METHODS;
    @JsonProperty("allow_credentials")
    private boolean allowCredentials = true;
    @JsonProperty("max_age")
    private int maxAge = 600;// 10min
    @JsonProperty("allow_headers")
    private Set<String> allowHeaders = DEF_ALLOW_HEADERS;
    @JsonProperty("expose_headers")
    private Set<String> exposeHeaders = DEF_ALLOW_EXPOSE;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public Set<String> getAllowOrigin() {
        return allowOrigin;
    }

    public void setAllowOrigin(Set<String> allowOrigin) {
        this.allowOrigin = allowOrigin;
    }

    public Set<HttpMethod> getAllowMethods() {
        return allowMethods;
    }

    public void setAllowMethods(Set<HttpMethod> allowMethods) {
        this.allowMethods = allowMethods;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public Set<String> getAllowHeaders() {
        return allowHeaders;
    }

    public void setAllowHeaders(Set<String> allowHeaders) {
        this.allowHeaders = allowHeaders;
    }

    public Set<String> getExposeHeaders() {
        return exposeHeaders;
    }

    public void setExposeHeaders(Set<String> exposeHeaders) {
        this.exposeHeaders = exposeHeaders;
    }

}