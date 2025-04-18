package io.fiber.net.common.ioc;

public interface Module {
    void install(Binder binder);

    default int order() {
        return 0;
    }
}
