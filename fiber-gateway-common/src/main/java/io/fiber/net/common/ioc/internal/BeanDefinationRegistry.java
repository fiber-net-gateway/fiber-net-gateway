package io.fiber.net.common.ioc.internal;


import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.utils.Predictions;

import java.util.*;
import java.util.function.Function;

public class BeanDefinationRegistry implements Binder {
    private final Map<Class<?>, Bean> beanMap = new HashMap<>();
    private Map<Class<?>, BeanList<?>> multiBeans = new HashMap<>();
    private Map<Class<?>, Class<?>> links = new HashMap<>();
    private BeanDefinationRegistry parent;

    private BeanDefinationRegistry() {
    }

    private <T> void put(Class<? super T> clz, Bean bean) {
        Bean old = beanMap.put(clz, bean);
        if (old != null || links.containsKey(clz)) {
            throw new IllegalStateException("bean " + clz.getName() + " exists");
        }
    }

    private <T> void replace(Class<? super T> clz, Bean bean) {
        beanMap.put(clz, bean);
        links.remove(clz);
    }

    Bean get(Class<?> clz) {
        return beanMap.get(clz);
    }

    @SuppressWarnings("unchecked")
    <V> BeanList<V> getBeans(Class<V> clz) {
        return (BeanList<V>) multiBeans.get(clz);
    }

    public Class<?> getLink(Class<?> clz) {
        return links.get(clz);
    }

    @Override
    public <T> void bind(Class<? super T> clz, T instance) {
        put(clz, Bean.ofInstance(instance));
    }

    @Override
    public <T> void forceBind(Class<? super T> clz, T instance) {
        replace(clz, Bean.ofInstance(instance));
    }

    @Override
    public <T> void bindFactory(Class<? super T> clz, Function<Injector, T> creator) {
        put(clz, Bean.ofFactory(creator));
    }

    @Override
    public <T> void bindLink(Class<? super T> clz, Class<T> real) {
        Predictions.assertTrue(clz != real, "cannot link self");
        Class<?> old = links.put(clz, real);
        if (old != null || beanMap.containsKey(clz)) {
            throw new IllegalStateException("link " + clz.getName() + " exists");
        }
    }

    @Override
    public <T> void forceBindFactory(Class<? super T> clz, Function<Injector, T> creator) {
        replace(clz, Bean.ofFactory(creator));
    }

    @Override
    public <T> void bindPrototype(Class<? super T> clz, Function<Injector, T> creator) {
        put(clz, Bean.ofPrototype(creator, clz));
    }

    @Override
    public <T> void forceBindPrototype(Class<? super T> clz, Function<Injector, T> creator) {
        replace(clz, Bean.ofPrototype(creator, clz));
    }

    @Override
    public <T> void forceBindLink(Class<? super T> clz, Class<T> real) {
        Predictions.assertTrue(clz != real, "cannot link self");
        links.put(clz, real);
        beanMap.remove(clz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, V extends T> void bindMultiBean(Class<T> clz, Class<V> real, int order) {
        BeanList<V> beanList = (BeanList<V>) multiBeans.computeIfAbsent(clz, k -> new BeanList<>());
        beanList.addClz(real, order);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, V extends T> void bindMultiBean(Class<T> clz, V real, int order) {
        BeanList<V> beanList = (BeanList<V>) multiBeans.computeIfAbsent(clz, k -> new BeanList<>());
        beanList.addObj(real, order);
    }

    @Override
    public <T> boolean removeBind(Class<T> clz) {
        return beanMap.remove(clz) != null || multiBeans.remove(clz) != null;
    }

    @Override
    public boolean contains(Class<?> clz) {
        return beanMap.containsKey(clz);
    }

    private boolean containsBean(Class<?> clz, boolean constainsLink) {
        return beanMap.containsKey(clz)
                || constainsLink && links.containsKey(clz)
                || parent != null && parent.containsBean(clz, constainsLink);
    }

    private void init(BeanDefinationRegistry parent) {
        this.parent = parent;
        Iterator<Map.Entry<Class<?>, Class<?>>> iterator = links.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Class<?>, Class<?>> entry = iterator.next();
            Class<?> key = entry.getKey();
            Class<?> real = entry.getValue();
            if (beanMap.containsKey(key)) {
                throw new IllegalStateException("link class is exists real bean:" + key.getName());
            }
            Bean bean = beanMap.get(real);
            if (bean != null) {
                beanMap.put(key, bean);
                iterator.remove();
            } else {
                if (parent == null || !parent.containsBean(real, false)) {
                    throw new IllegalStateException("bean not found:" + real);
                }
            }
        }
        if (links.isEmpty()) {
            links = Collections.emptyMap();
        }

        if (multiBeans.isEmpty()) {
            multiBeans = Collections.emptyMap();
        } else {
            for (Map.Entry<Class<?>, BeanList<?>> next : multiBeans.entrySet()) {
                next.getValue().sort();
                for (BeanList.BeanElement element : next.getValue().getBeanElements()) {
                    if (element.isClzBean() && !containsBean(element.getClz(), true)) {
                        throw new IllegalStateException("bean not found:" + element.getClz());
                    }
                }
            }
        }
    }

    public static BeanDefinationRegistry ofModules(BeanDefinationRegistry parent, Iterable<Module> modules) {
        BeanDefinationRegistry registry = new BeanDefinationRegistry();
        ArrayList<Module> ms = new ArrayList<>();
        modules.forEach(ms::add);
        ms.sort(Comparator.comparingInt(Module::order));
        for (Module module : ms) {
            module.install(registry);
        }
        registry.init(parent);
        return registry;
    }
}
