package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.ioc.Injector;

public class HttpEngine extends Engine<HttpExchange> {
    public HttpEngine(Injector injector) {
        super(injector);
    }

    @Override
    protected void runInternal(RouterHandler<HttpExchange> router, HttpExchange httpExchange) throws Exception {
        if (router == null) {
            httpExchange.discardReqBody();
            httpExchange.writeJson(404, "NOT_FOUND");
            return;
        }

        router.invoke(httpExchange);
    }
}
