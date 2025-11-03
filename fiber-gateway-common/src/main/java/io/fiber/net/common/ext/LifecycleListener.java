package io.fiber.net.common.ext;


import io.fiber.net.common.Server;

public interface LifecycleListener {
    enum Event {
        INIT,
        STARTED,
        PRE_STOP,
        STOPPED
    }

    void onEvent(Server<?> server, Event event) throws Exception;
}
