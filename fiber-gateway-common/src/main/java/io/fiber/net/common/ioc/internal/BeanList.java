package io.fiber.net.common.ioc.internal;

import java.util.ArrayList;
import java.util.List;

class BeanList<V> {


    static class BeanElement implements Comparable<BeanElement> {
        private final boolean clzBean;
        private final Object obj;
        private final int order;

        private BeanElement(boolean clzBean, Object obj, int order) {
            this.clzBean = clzBean;
            this.obj = obj;
            this.order = order;
        }

        static BeanElement forClz(Class<?> clz, int order) {
            return new BeanElement(true, clz, order);
        }

        static BeanElement forObj(Object obj, int order) {
            return new BeanElement(false, obj, order);
        }

        @Override
        public int compareTo(BeanElement o) {
            return order - o.order;
        }

        public boolean isClzBean() {
            return clzBean;
        }

        public Class<?> getClz() {
            return (Class<?>) obj;
        }
        public Object getObj() {
            return obj;
        }
    }

    private final List<BeanElement> beanElements = new ArrayList<>();

    public void addClz(Class<V> clz, int order) {
        beanElements.add(BeanElement.forClz(clz, order));
    }

    public void addObj(V obj, int order) {
        beanElements.add(BeanElement.forObj(obj, order));
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
