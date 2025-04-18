package io.fiber.net.server;

import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Injector;

public class HttpServerStartListener implements StartListener<HttpExchange, HttpEngine> {
    private final HttpServer server;

    public HttpServerStartListener(Injector injector) {
        server = injector.getInstance(HttpServer.class);
    }

    @Override
    public final void onStart(HttpEngine engine) throws Exception {
        beforeServerStart(engine);
        server.start(new ServerConfig(), engine);
        afterStartEnd(engine);
    }

    protected void beforeServerStart(HttpEngine engine) throws Exception {
    }

    protected void afterStartEnd(HttpEngine engine) throws Exception {
    }
}
