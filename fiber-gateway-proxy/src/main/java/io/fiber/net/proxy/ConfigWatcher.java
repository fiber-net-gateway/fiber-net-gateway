package io.fiber.net.proxy;

import io.fiber.net.server.HttpEngine;

public interface ConfigWatcher {
    ConfigWatcher NOOP_WATCHER = engine -> {
    };

    void startWatch(HttpEngine engine) throws Exception;
}
