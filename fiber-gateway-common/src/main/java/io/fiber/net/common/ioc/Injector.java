package io.fiber.net.common.ioc;

import io.fiber.net.common.ioc.internal.BeanDefinationRegistry;
import io.fiber.net.common.ioc.internal.InjectorImpl;
import io.fiber.net.common.ioc.internal.RootInjector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public abstract class Injector {
    public static Injector getRoot() {
        return RootInjector.INSTANCE;
    }

    public abstract Injector getParent();

    public abstract Injector fork();

    public abstract Injector deepFork(Predicate<Injector> shouldFork);

    public final Injector createChild(Module... modules) {
        if (ArrayUtils.isEmpty(modules)) {
            throw new IllegalArgumentException("empty modules");
        }
        return createChild(Arrays.asList(modules));
    }

    public final Injector createChild(Collection<Module> modules) {
        if (CollectionUtils.isEmpty(modules)) {
            throw new IllegalArgumentException("empty modules");
        }
        BeanDefinationRegistry parentRegistry = this instanceof InjectorImpl
                ? ((InjectorImpl) this).getRegistry() : null;
        BeanDefinationRegistry registry = BeanDefinationRegistry.ofModules(parentRegistry, modules);
        return new InjectorImpl(this, registry);
    }

    public abstract <T> T getInstance(Class<T> clz);

    public abstract <T> T[] getInstances(Class<T> clz);

    public abstract void destroy();

}
