package io.fiber.net.common.ioc;

import java.util.function.Function;

public interface Binder {
    <T> void bind(Class<? super T> clz, T instance);

    <T> void forceBind(Class<? super T> clz, T instance);

    <T> void bindFactory(Class<? super T> clz, Function<Injector, T> creator);

    <T> void bindLink(Class<? super T> clz, Class<T> real);

    <T> void forceBindFactory(Class<? super T> clz, Function<Injector, T> creator);

    <T> void bindPrototype(Class<? super T> clz, Function<Injector, T> creator);

    <T> void forceBindPrototype(Class<? super T> clz, Function<Injector, T> creator);

    <T> void forceBindLink(Class<? super T> clz, Class<T> real);

    default <T, V extends T> void bindMultiBean(Class<T> clz, Class<V> real) {
        bindMultiBean(clz, real, 0);
    }

    default <T, V extends T> void bindMultiBean(Class<T> clz, T real) {
        bindMultiBean(clz, real, 0);
    }

    <T, V extends T> void bindMultiBean(Class<T> clz, Class<V> real, int order);

    <T, V extends T> void bindMultiBean(Class<T> clz, V real, int order);


    <T> boolean removeBind(Class<T> clz);

    boolean contains(Class<?> clz);
}
