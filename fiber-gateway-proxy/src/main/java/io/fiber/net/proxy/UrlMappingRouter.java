package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.proxy.lib.ReqFunc;
import io.fiber.net.script.run.Compares;
import io.fiber.net.server.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UrlMappingRouter implements RouterHandler<HttpExchange> {
    private final String name;
    private RoutePathMatcher<RouterHandler<HttpExchange>> matcher;
    List<UrlHandlerManager.ScriptRef> refs;

    public UrlMappingRouter(String name) {
        this.name = name;
    }

    @Override
    public String getRouterName() {
        return name;
    }

    public void setMatcher(RoutePathMatcher<RouterHandler<HttpExchange>> matcher) {
        this.matcher = matcher;
    }

    public void setRefs(List<UrlHandlerManager.ScriptRef> refs) {
        this.refs = refs;
    }

    @Override
    public void invoke(HttpExchange exchange) throws Exception {
        if (exchange.getRequestMethod() == HttpMethod.CONNECT) {
            exchange.discardReqBody();
            exchange.writeJson(501, "NO_IMPLEMENTS");
            return;
        }
        RoutePathMatcher.MappingResult<RouterHandler<HttpExchange>> result = matcher.matchPath(exchange);
        RouterHandler<HttpExchange> handler = result.getHandler();
        if (handler == null) {
            exchange.discardReqBody();
            exchange.writeJson(404, "URL_NOT_MATCHED");
            return;
        }

        if (!result.isForCors()) {
            ReqFunc.MAPPING_RESULT_ATTR.set(exchange, result);
        }
        handler.invoke(exchange);
    }

    @Override
    public void destroy() {
        if (CollectionUtils.isNotEmpty(refs)) {
            for (UrlHandlerManager.ScriptRef ref : refs) {
                ref.destroy();
            }
        }
    }

    public static Builder builder(UrlHandlerManager urlHandlerManager) {
        return new Builder(urlHandlerManager);
    }

    public static class Builder {

        private final UrlHandlerManager urlHandlerManager;
        private CorsConfig defaultCorsConfig;
        private List<UrlRoute> routes;
        private String name;
        private UrlMappingRouter router;

        Builder(UrlHandlerManager urlHandlerManager) {
            this.urlHandlerManager = urlHandlerManager;
        }

        public Builder setDefaultCorsConfig(CorsConfig defaultCorsConfig) {
            this.defaultCorsConfig = defaultCorsConfig;
            return this;
        }

        public Builder setRoutes(List<UrlRoute> routes) {
            this.routes = routes;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public UrlMappingRouter build() throws Exception {
            router = new UrlMappingRouter(name);
            router.setRefs(new ArrayList<>());
            try {
                buildRoutes();
            } catch (Exception e) {
                router.destroy();
                throw e;
            }
            return router;
        }

        private void buildRoutes() throws IOException {
            RoutePathMatcher.Builder<RouterHandler<HttpExchange>> builder = RoutePathMatcher.builder();
            for (UrlRoute urlRoute : routes) {
                String m = urlRoute.getMethod(), f = urlRoute.getFile(), u = urlRoute.getUrl();
                Assert.isTrue(StringUtils.isNotEmpty(urlRoute.getUrl())
                                && StringUtils.isNotEmpty(urlRoute.getFile())
                                && StringUtils.isNotEmpty(m),
                        "url and method are both required"
                );

                HttpMethod hm = HttpMethod.resolve(m);
                if (hm == null && !"*".equals(m)) {
                    throw new IllegalStateException("invalid method:" + m);
                }

                UrlHandlerManager.ScriptRef scriptRef = urlHandlerManager.getOrCreate(f);
                router.refs.add(scriptRef);
                JsonNode cors = urlRoute.getCors();
                if (Compares.logic(cors)) {
                    if (cors.isObject()) {
                        CorsConfig corsConfig = JsonUtil.treeToValue(cors, CorsConfig.class);
                        builder.addUrlHandler(hm, u, CorsScriptHandler.create(scriptRef.getHandler(), corsConfig));
                        continue;
                    }
                    if (cors.asBoolean()) {
                        builder.addUrlHandler(hm, u, CorsScriptHandler.create(scriptRef.getHandler(), defaultCorsConfig));
                        continue;
                    }
                }
                ScriptHandler handler = scriptRef.getHandler();
                builder.addUrlHandler(hm, u, handler);
            }
            router.setMatcher(builder.build());
        }
    }
}
