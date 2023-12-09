package io.fiber.net.proxy;

import io.fiber.net.common.Engine;

public interface ConfigWatcher {
    ConfigWatcher NOOP_WATCHER = engine -> {
    };

    void startWatch(Engine engine) throws Exception;
}
