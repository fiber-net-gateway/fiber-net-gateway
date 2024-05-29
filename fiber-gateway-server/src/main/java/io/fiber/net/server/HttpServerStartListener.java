package io.fiber.net.server;

import io.fiber.net.common.ext.StartListener;

public class HttpServerStartListener implements StartListener<HttpExchange, HttpEngine> {

    @Override
    public final void onStart(HttpEngine engine) throws Exception {
        beforeServerStart(engine);
        HttpServer server = engine.getInjector().getInstance(HttpServer.class);
        server.start(new ServerConfig(), engine);
        afterStartEnd(engine);
    }

    protected void beforeServerStart(HttpEngine engine) throws Exception {
    }

    protected void afterStartEnd(HttpEngine engine) throws Exception {
    }
}
