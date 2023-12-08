package io.fiber.net.common.ioc.internal;


import io.fiber.net.common.ioc.Injector;

import java.util.function.Predicate;

public class RootInjector extends Injector {
    public static final RootInjector INSTANCE = new RootInjector();

    private RootInjector() {
    }

    @Override
    public Injector getParent() {
        return null;
    }

    @Override
    public Injector fork() {
        return this;
    }

    @Override
    public Injector deepFork(Predicate<Injector> shouldFork) {
        return this;
    }

    @Override
    public <T> T getInstance(Class<T> clz) {
        throw new IllegalStateException(clz.getName() + " not found");
    }

    @Override
    public <T> T[] getInstances(Class<T> clz) {
        return null;
    }

    @Override
    public void destroy() {
    }
}
