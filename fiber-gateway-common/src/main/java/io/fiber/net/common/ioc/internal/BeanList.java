package io.fiber.net.common.ioc.internal;

import io.fiber.net.common.ioc.Injector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class BeanList<V> {

    public static final int CT_CLZ = 0;
    public static final int CT_INS = 1;
    public static final int CT_CTR = 2;

    static class BeanElement implements Comparable<BeanElement> {
        private final int type;
        private final Object obj;
        private final int order;

        private BeanElement(int type, Object obj, int order) {
            this.type = type;
            this.obj = obj;
            this.order = order;
        }

        static BeanElement forClz(Class<?> clz, int order) {
            return new BeanElement(CT_CLZ, clz, order);
        }

        static BeanElement forObj(Object obj, int order) {
            return new BeanElement(CT_INS, obj, order);
        }

        static BeanElement forCtr(Function<Injector, ?> obj, int order) {
            return new BeanElement(CT_CTR, obj, order);
        }

        @Override
        public int compareTo(BeanElement o) {
            return order - o.order;
        }

        public int getType() {
            return type;
        }

        public Class<?> getClz() {
            if (type != CT_CLZ) {
                throw new IllegalStateException("not clz bean");
            }
            return (Class<?>) obj;
        }

        public Object getObj() {
            if (type != CT_INS) {
                throw new IllegalStateException("not obj bean");
            }
            return obj;
        }

        @SuppressWarnings("unchecked")
        public Function<Injector, ?> getCtr() {
            if (type != CT_CTR) {
                throw new IllegalStateException("not ctr bean");
            }
            return (Function<Injector, ?>) obj;
        }

        public boolean isClz() {
            return type == CT_CLZ;
        }

        public boolean isObj() {
            return type == CT_INS;
        }

        public boolean isCtr() {
            return type == CT_CTR;
        }
    }

    private final List<BeanElement> beanElements = new ArrayList<>();

    public void addClz(Class<V> clz, int order) {
        beanElements.add(BeanElement.forClz(clz, order));
    }

    public void addObj(V obj, int order) {
        beanElements.add(BeanElement.forObj(obj, order));
    }

    public void addCreator(Function<Injector, ? extends V> obj, int order) {
        beanElements.add(BeanElement.forCtr(obj, order));
    }

    public void sort() {
        beanElements.sort(BeanElement::compareTo);
    }

    List<BeanElement> getBeanElements() {
        return beanElements;
    }

    public int size() {
        return beanElements.size();
    }
}
