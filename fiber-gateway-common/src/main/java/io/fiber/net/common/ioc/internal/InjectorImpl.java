package io.fiber.net.common.ioc.internal;


import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Initializable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Predictions;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * cycle dependency maybe cause deadlock
 */
public class InjectorImpl extends Injector {
    private static final Map<Bean, Creator> DESTROYED = Collections.emptyMap();
    private static final Function<Bean, Creator> CREATING_FUNC = Creator::new;
    private static final Object CREATING = new Object();
    private static final Object NULL = new Object();
    private final Injector parent;
    private final BeanDefinationRegistry registry;

    private final Stack<Destroyable> destroyables = new Stack<>();
    private volatile Map<Bean, Creator> instance = new ConcurrentHashMap<>();

    public InjectorImpl(Injector parent, BeanDefinationRegistry registry) {
        this.parent = parent;
        this.registry = registry;
    }

    @Override
    public Injector getParent() {
        return parent;
    }

    @Override
    public Injector fork() {
        return new InjectorImpl(parent, registry);
    }

    @Override
    public Injector deepFork(Predicate<Injector> shouldFork) {
        Injector parent = this.parent;
        if (parent != RootInjector.getRoot() && shouldFork.test(parent)) {
            parent = parent.deepFork(shouldFork);
        }

        return new InjectorImpl(parent, registry);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <T> T getInstance(Class<T> clz) {
        final Map<Bean, Creator> instance = this.instance;
        Predictions.assertTrue(instance != DESTROYED, "destroyed");
        Bean bean = registry.get(clz);
        if (bean == null) {
            Class<?> link = registry.getLink(clz);
            if (link != null) {
                clz = (Class<T>) link;
            }
            return parent.getInstance(clz);
        }
        if (bean.isFactory()) {
            Creator creator = instance.computeIfAbsent(bean, CREATING_FUNC);
            return (T) creator.create(this);
        }

        return (T) bean.get(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getInstances(Class<T> clz) {
        final Map<Bean, Creator> instance = this.instance;
        Predictions.assertTrue(instance != DESTROYED, "destroyed");
        BeanList beans = registry.getBeans(clz);
        if (beans == null) {
            return parent.getInstances(clz);
        }
        int size = beans.size();
        int i = 0;
        T[] result = (T[]) Array.newInstance(clz, size);
        for (Class<?> id : beans) {
            result[i++] = (T) getInstance(id);
        }
        return result;
    }

    public BeanDefinationRegistry getRegistry() {
        return registry;
    }

    private void addDestroyable(Destroyable destroyable) {
        Map<Bean, Creator> instance = this.instance;
        Stack<Destroyable> destroyables = this.destroyables;
        if (instance == DESTROYED) {
            synchronized (destroyables) {
                destroyable.destroy();
            }
            throw new IllegalStateException("destroyed");
        }
        synchronized (destroyables) {
            instance = this.instance;
            Predictions.assertTrue(instance != DESTROYED, "destroyed");
            destroyables.add(destroyable);
        }
    }

    @Override
    public void destroy() {
        Map<Bean, Creator> instance = this.instance;
        Predictions.assertTrue(instance != DESTROYED, "destroyed");
        Stack<Destroyable> destroyables = this.destroyables;
        synchronized (destroyables) {
            instance = this.instance;
            Predictions.assertTrue(instance != DESTROYED, "destroyed");
            this.instance = DESTROYED;
            while (!destroyables.empty()) {
                destroyables.pop().destroy();
            }
        }
    }

    private static class Creator {
        private final Bean bean;
        private Object infant;
        private volatile Object instance;


        private Creator(Bean bean) {
            this.bean = bean;
        }

        private Object create(InjectorImpl injector) {
            Object object = instance;
            if (object != null && object != CREATING) {
                return object == NULL ? null : object;
            }
            synchronized (this) {
                object = instance;
                if (object != null && object != CREATING) {
                    return object == NULL ? null : object;
                }

                if (object == CREATING) {
                    if (infant != null) {
                        return infant;
                    }

                    throw new IllegalStateException("cycle dep");
                }

                instance = CREATING;

                Object o = bean.get(injector);
                if (o == null) {
                    instance = NULL;
                    return null;
                }
                infant = o;
                if (o instanceof Initializable) {
                    ((Initializable) o).init();
                }
                instance = object = o;
                infant = null;
            }

            if (object instanceof Destroyable) {
                injector.addDestroyable((Destroyable) object);
            }

            return object;
        }
    }
}
