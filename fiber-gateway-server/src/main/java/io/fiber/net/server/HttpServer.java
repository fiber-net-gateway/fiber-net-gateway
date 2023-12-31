package io.fiber.net.server;


import io.fiber.net.common.Engine;
import io.fiber.net.common.ioc.Destroyable;

public interface HttpServer extends Destroyable {
    void start(ServerConfig config, Engine engine) throws Exception;

    void awaitShutdown();
}
