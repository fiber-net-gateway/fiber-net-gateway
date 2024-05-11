package io.fiber.net.proxy;

import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.proxy.lib.ReqFunc;
import io.fiber.net.server.HttpExchange;

import java.util.List;

public class UrlMappingRouter implements RouterHandler<HttpExchange> {
    private final String name;
    private RoutePathMatcher<ScriptHandler> matcher;
    private List<UrlHandlerManager.ScriptRef> refs;

    public UrlMappingRouter(String name) {
        this.name = name;
    }

    @Override
    public String getRouterName() {
        return name;
    }

    public void setMatcher(RoutePathMatcher<ScriptHandler> matcher) {
        this.matcher = matcher;
    }

    public void setRefs(List<UrlHandlerManager.ScriptRef> refs) {
        this.refs = refs;
    }

    @Override
    public void invoke(HttpExchange exchange) throws Exception {
        RoutePathMatcher.MappingResult<ScriptHandler> result = matcher.matchPath(exchange);
        ScriptHandler script = result.getHandler();
        if (script == null) {
            exchange.discardReqBody();
            exchange.writeJson(404, "URL_NOT_MATCHED");
            return;
        }

        ReqFunc.MAPPING_RESULT_ATTR.set(exchange, result);
        script.invoke(exchange);
    }

    @Override
    public void destroy() {
        if (CollectionUtils.isNotEmpty(refs)) {
            for (UrlHandlerManager.ScriptRef ref : refs) {
                ref.destroy();
            }
        }
    }
}
