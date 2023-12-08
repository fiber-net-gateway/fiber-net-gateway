package io.fiber.net.common.ioc.internal;

import java.util.LinkedHashSet;

class BeanList extends LinkedHashSet<Class<?>> {
    public BeanList(Class<?> clz) {
        super(4);
        add(clz);
    }

    public BeanList(Class<?> clz1, Class<?> clz2) {
        super(4);
        add(clz1);
        add(clz2);
    }

    public BeanList(Class<?> clz1, Class<?> clz2, Class<?> clz3) {
        super(4);
        add(clz1);
        add(clz2);
        add(clz3);
    }
}
