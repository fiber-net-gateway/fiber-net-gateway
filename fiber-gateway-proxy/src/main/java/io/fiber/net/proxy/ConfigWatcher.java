package io.fiber.net.proxy;

import io.fiber.net.server.HttpServer;

public interface ConfigWatcher {
    ConfigWatcher NOOP_WATCHER = engine -> {
    };

    void startWatch(HttpServer engine) throws Exception;
}
