package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.Unpooled;

import java.util.Collection;
import java.util.stream.Collectors;

public class CorsScriptHandler implements RouterHandler<HttpExchange>, CorsProcessor {
    private final ScriptHandler handler;

    public CorsScriptHandler(ScriptHandler handler) {
        this.handler = handler;
    }

    private final WildHostNode root = new WildHostNode();
    private String allowMethods;
    private boolean allowCredentials;
    private String maxAge;
    private String allowHeaders;
    private String exposeHeaders;

    public void setAllowMethods(Collection<HttpMethod> allowMethods) {
        if (CollectionUtils.isEmpty(allowMethods)) {
            this.allowMethods = null;
        } else {
            this.allowMethods = allowMethods.stream().map(Enum::name).collect(Collectors.joining(", "));
        }
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public void setMaxAge(int maxAge) {
        if (maxAge > 0) {
            this.maxAge = Integer.toString(maxAge);
        } else {
            this.maxAge = null;
        }
    }

    public void setAllowHeaders(Collection<String> allowHeaders) {
        if (CollectionUtils.isEmpty(allowHeaders)) {
            this.allowHeaders = null;
        } else {
            this.allowHeaders = String.join(", ", allowHeaders);
        }
    }

    public void setAllowOriginDomains(Collection<String> allowOriginDomains) {
        if (CollectionUtils.isNotEmpty(allowOriginDomains)) {
            for (String originDomain : allowOriginDomains) {
                if (StringUtils.isNotEmpty(originDomain)) {
                    root.addDomainPattern(originDomain);
                }
            }
        }
    }

    public void setExposeHeaders(Collection<String> exposeHeaders) {
        if (CollectionUtils.isEmpty(exposeHeaders)) {
            this.exposeHeaders = null;
        } else {
            this.exposeHeaders = String.join(", ", exposeHeaders);
        }
    }

    private boolean matchOrigin(String origin) {
        if (StringUtils.isEmpty(origin)) {
            return false;
        }
        int s = 0;
        if (origin.startsWith("https://")) {
            s = 8;
        } else if (origin.startsWith("http://")) {
            s = 7;
        }

        return root.matchHost(origin, s);
    }

    private void writeHeadersForPreflight(HttpExchange exchange, String origin) {
        exchange.setResponseHeader("Access-Control-Allow-Origin", origin);
        exchange.addResponseHeader("Vary", "Origin");

        String allowMethods = this.allowMethods;
        boolean allowCredentials = this.allowCredentials;
        String maxAge = this.maxAge;
        String allowHeaders = this.allowHeaders;
        String exposeHeaders = this.exposeHeaders;

        if (allowMethods != null) {
            exchange.setResponseHeader("Access-Control-Allow-Methods", allowMethods);
        }
        if (allowCredentials) {
            exchange.setResponseHeader("Access-Control-Allow-Credentials", "true");
        }
        if (maxAge != null) {
            exchange.setResponseHeader("Access-Control-Max-Age", maxAge);
        }
        if (allowHeaders != null) {
            exchange.setResponseHeader("Access-Control-Allow-Headers", allowHeaders);
        }
        if (exposeHeaders != null) {
            exchange.setResponseHeader("Access-Control-Expose-Headers", exposeHeaders);
        }
    }

    @Override
    public boolean processCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeader(ORIGIN_HEADER);
        boolean matchOrigin = matchOrigin(origin);
        boolean preflightReq = CorsProcessor.isPreflightReq(exchange, origin);
        if (preflightReq) {
            if (matchOrigin) {
                writeHeadersForPreflight(exchange, origin);
            }
            return true;
        }

        if (matchOrigin) {
            exchange.setResponseHeader("Access-Control-Allow-Origin", origin);
            exchange.addResponseHeader("Vary", ORIGIN_HEADER);

            String exposeHeaders;
            if ((exposeHeaders = this.exposeHeaders) != null) {
                exchange.setResponseHeader("Access-Control-Expose-Headers", exposeHeaders);
            }

            if (allowCredentials) {
                exchange.setResponseHeader("Access-Control-Allow-Credentials", "true");
            }
        }
        return false;
    }

    @Override
    public String getRouterName() {
        return handler.getRouterName();
    }

    @Override
    public void invoke(HttpExchange exchange) throws Exception {
        if (processCors(exchange)) {
            exchange.writeRawBytes(204, Unpooled.EMPTY_BUFFER);
            return;
        }
        handler.invoke(exchange);
    }

    @Override
    public void destroy() {
        handler.destroy();
    }

    public static CorsScriptHandler create(ScriptHandler scriptHandler, CorsConfig corsConfig) {
        CorsScriptHandler handler = new CorsScriptHandler(scriptHandler);
        handler.setAllowHeaders(corsConfig.getAllowHeaders());
        handler.setAllowCredentials(corsConfig.isAllowCredentials());
        handler.setAllowOriginDomains(corsConfig.getAllowOrigin());
        handler.setMaxAge(corsConfig.getMaxAge());
        handler.setExposeHeaders(corsConfig.getExposeHeaders());
        handler.setAllowMethods(corsConfig.getAllowMethods());
        return handler;
    }

}
