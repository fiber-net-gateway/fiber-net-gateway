package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class HttpExchange {
    /**
     * 尽量把 HttpExchange 这个类的性能优化得非常好
     *
     * @param <T>
     */
    public static final class Attr<T> {
        private static final AtomicInteger _ID_GEN = new AtomicInteger();
        private static int ATTR_CAP = 8; // 不必使用volatile

        private static int currentCap() {
            return ATTR_CAP = _ID_GEN.get() + 2;// 留意下一个buffer。
        }

        private final int id;

        private Attr() {
            this.id = _ID_GEN.getAndIncrement();
            // 这里可能产生并发问题，不过没关系，使用 attr 的时候会修正这个值。而且流有buffer
            ATTR_CAP = Integer.max(ATTR_CAP, _ID_GEN.get() + 2);
        }

        public void set(HttpExchange input, T value) {
            Object[] arr;
            if ((arr = input.attrs) == null) {
                arr = input.attrs = new Object[ATTR_CAP];
            }

            int idx;
            if ((idx = this.id) >= arr.length) {
                int cap = Attr.currentCap();
                assert cap > arr.length;
                arr = input.attrs = Arrays.copyOf(arr, cap);
            }
            arr[idx] = value;
        }

        @SuppressWarnings("unchecked")
        public T get(HttpExchange input) {
            Object[] arr;
            int idx;
            if ((arr = input.attrs) == null || (idx = this.id) >= arr.length) {
                return null;
            }
            return (T) arr[idx];
        }

        @SuppressWarnings("unchecked")
        public T remove(HttpExchange input) {
            Object[] arr;
            int idx;
            if ((arr = input.attrs) == null || (idx = this.id) >= arr.length) {
                return null;
            }
            T old = (T) arr[idx];
            arr[idx] = null;
            return old;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    /**
     * 不要乱调用 ！！！！！！！！
     * <pre>
     * private static final Input.Attr&lt;ObjectNode&gt; AUDIT_LOG = Input.createAttr();
     *
     * ObjectNode node = AUDIT_LOG.get(input);
     * AUDIT_LOG.set(input, value);
     * </pre>
     *
     * @param <T> KEY,
     * @return attr  ！！！保存到全局，不要随意创建！！！否则会照成内存泄漏或者溢出
     */
    public static <T> Attr<T> createAttr() {
        return new Attr<>();
    }

    private Object[] attrs;

    public abstract String getPath();

    public abstract String getQuery();

    public abstract String getUri();

    public abstract void setRequestHeader(String name, String value);

    public abstract void addRequestHeader(String name, String value);

    public abstract String getRequestHeader(String name);

    public abstract List<String> getRequestHeaderList(String name);

    public abstract Collection<String> getRequestHeaderNames();

    public abstract void setResponseHeader(String name, String value);

    public abstract void addResponseHeader(String name, String value);

    public abstract void removeResponseHeader(String name);

    public abstract String getResponseHeader(String name);

    public abstract List<String> getResponseHeaderList(String name);

    public abstract Collection<String> getResponseHeaderNames();

    public abstract HttpMethod getRequestMethod();

    public abstract void writeJson(int status, Object result) throws FiberException;

    public abstract void writeRawBytes(int status, ByteBuf buf) throws FiberException;

    public void writeRawBytes(int status, Observable<ByteBuf> bufOb) throws FiberException {
        writeRawBytes(status, bufOb, false);
    }

    public abstract void writeRawBytes(int status, Observable<ByteBuf> bufOb, boolean flush) throws FiberException;

    public abstract boolean isResponseWrote();

    public abstract void discardReqBody();


    public Observable<ByteBuf> readBody() {
        return readBodyUnsafe().notifyOn(Scheduler.current());
    }

    public Observable<ByteBuf> readBody(Scheduler scheduler) {
        return readBodyUnsafe().notifyOn(scheduler);
    }

    /**
     * not notify cross thread. for performance.
     *
     * @return ob for body
     */
    public abstract Observable<ByteBuf> readBodyUnsafe();

    public Maybe<ByteBuf> readFullBody() {
        return readFullBody(Scheduler.current());
    }

    public abstract Maybe<ByteBuf> readFullBody(Scheduler scheduler);

}
