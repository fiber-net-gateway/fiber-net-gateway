package io.fiber.net.example.route;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.ext.RouterHandler;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.proxy.route.RouteConflictException;
import io.fiber.net.proxy.route.RoutePathMatcher;
import io.fiber.net.proxy.route.VarType;
import io.fiber.net.script.run.Compares;
import io.fiber.net.server.HttpExchange;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UrlMappingRouter implements RouterHandler<HttpExchange> {
    private final String name;
    private RoutePathMatcher<MethodSlotHandler> matcher;
    List<UrlHandlerManager.ScriptRef> refs;

    public UrlMappingRouter(String name) {
        this.name = name;
    }

    @Override
    public String getRouterName() {
        return name;
    }

    public void setMatcher(RoutePathMatcher<MethodSlotHandler> matcher) {
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
        RouteContext routeContext = new RouteContext(matcher.getMaxPathVarLength(), exchange);
        boolean matched = matcher.matchPath(exchange.getPath(), routeContext);
        if (!matched) {
            exchange.discardReqBody();
            exchange.writeJson(404, "URL_NOT_MATCHED");
            return;
        }

        exchange.checkMaxReqBodySize();
        routeContext.invokeHandler();
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

    public static class Builder implements RoutePathMatcher.RouteVarDefiner<UrlRoute, MethodSlotHandler> {

        private final UrlHandlerManager urlHandlerManager;
        private CorsConfig defaultCorsConfig;
        private List<UrlRoute> routes;
        private String name;
        private UrlMappingRouter router;
        private IntObjectMap<MethodSlotHandler> methodSlotHandlers = new IntObjectHashMap<>();


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
            RoutePathMatcher.Builder<UrlRoute, MethodSlotHandler> builder = RoutePathMatcher.builder(this);
            for (UrlRoute urlRoute : routes) {
                builder.addUrlHandler(urlRoute.getUrl(), urlRoute);
            }
            router.setMatcher(builder.build());
        }

        @Override
        public void addPathVarDefiner(UrlRoute builder, String varName, int idx) {
            builder.varDefinitions.add(varName);
        }

        @Override
        public MethodSlotHandler onRouteMount(int routeNodeId, UrlRoute urlRoute) throws RouteConflictException {
            String m = urlRoute.getMethod(), f = urlRoute.getFile(), u = urlRoute.getUrl();
            Assert.isTrue(StringUtils.isNotEmpty(u)
                            && StringUtils.isNotEmpty(urlRoute.getFile())
                            && StringUtils.isNotEmpty(m),
                    "url and method are both required");

            MethodSlotHandler h = methodSlotHandlers.computeIfAbsent(routeNodeId, k -> new MethodSlotHandler(u));

            HttpMethod hm = HttpMethod.resolve(m);
            if (hm == null && !"*".equals(m)) {
                throw new IllegalStateException("invalid method:" + m);
            }

            UrlHandlerManager.ScriptRef scriptRef = urlHandlerManager.getOrCreate(f);
            router.refs.add(scriptRef);
            SimpleScriptHandler handler = scriptRef.getHandler();

            int size = urlRoute.varDefinitions.size();
            if (size > 0) {
                int[] idxMap = new int[size];
                Arrays.fill(idxMap, -1);
                handler.getVarConfigSource().forEach(VarType.PATH, varConst -> {
                    int i = urlRoute.varDefinitions.indexOf(varConst.getNameTxt());
                    if (i < 0) {
                        throw new RouteConflictException("path var not defined:" + varConst.getNameTxt());
                    }
                    idxMap[i] = varConst.getIdx();
                });
                handler.setVarDefinitions(idxMap);
            }


            JsonNode cors = urlRoute.getCors();
            if (!Compares.logic(cors)) {
                h.addHandler(hm, handler);
                return h;
            }

            CorsConfig corsConfig;
            if (cors.isObject()) {
                try {
                    corsConfig = JsonUtil.treeToValue(cors, CorsConfig.class);
                } catch (IOException e) {
                    throw new IllegalStateException("cannot convert to CorsConfig", e);
                }
            } else if (cors.asBoolean()) {
                corsConfig = defaultCorsConfig;
            } else {
                throw new IllegalStateException("invalid cors config");
            }

            h.addHandler(hm, CorsScriptHandler.create(scriptRef.getHandler(), corsConfig));
            return h;
        }
    }
}
