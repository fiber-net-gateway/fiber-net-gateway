package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.proxy.lib.ReqFunc;
import io.fiber.net.server.HttpExchange;

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

}
