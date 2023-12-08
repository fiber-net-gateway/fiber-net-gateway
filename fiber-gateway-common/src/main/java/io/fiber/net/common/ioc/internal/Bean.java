package io.fiber.net.common.ioc.internal;


import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;

import java.util.function.Function;

class Bean {
    private static final int TYPE_PRESENT = 1;
    private static final int TYPE_CONSTRUCT = 2;
    private static final int TYPE_PROTO = 4;

    public static <T> Bean ofInstance(T instance) {
        return new Bean(TYPE_PRESENT, instance, null);
    }

    public static <T> Bean ofFactory(Function<Injector, T> creator) {
        return new Bean(TYPE_CONSTRUCT, null, creator);
    }

    public static <T> Bean ofPrototype(Function<Injector, T> creator, Class<? super T> id) {
        if (Destroyable.class.isAssignableFrom(id)) {
            throw new IllegalArgumentException("Destroyable bean must not be prototype:" + id.getName());
        }
        return new Bean(TYPE_PROTO, null, creator);
    }

    private final int type;
    private final Object instance;
    private final Function<Injector, ?> creator;


    private Bean(int type, Object instance, Function<Injector, ?> creator) {
        this.type = type;
        this.instance = instance;
        this.creator = creator;
    }

    public Object get(Injector injector) {
        switch (type) {
            case TYPE_PRESENT:
                return instance;
            case TYPE_CONSTRUCT:
            case TYPE_PROTO:
                return creator.apply(injector);
            default:
                throw new UnsupportedOperationException("not hit");
        }
    }

    public boolean isFactory() {
        return type == TYPE_CONSTRUCT;
    }

    public boolean isPrototype() {
        return type == TYPE_PROTO;
    }


}
