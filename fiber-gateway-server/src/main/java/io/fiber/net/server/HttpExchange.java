package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.utils.UnsafeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.FileRegion;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class HttpExchange {
    protected static final Logger log = LoggerFactory.getLogger(HttpExchange.class);

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

    public interface Listener {
        default void onClientAbort(HttpExchange exchange) {
        }

        default void onHeaderSend(HttpExchange exchange, int status) {
        }

        default void onBodySent(HttpExchange exchange, Throwable respErr) {
        }
    }


    protected static final class FilterInvocation {
        private static final int FIELD_SZ = 6;
        private static final long DST_OFT;
        private static final long DST_SZ;
        // optimize memory alloc

        static {
            long s, e;
            try {
                s = UnsafeUtil.fieldOffset(FilterInvocation.class.getDeclaredField("listener_0"));
                e = UnsafeUtil.fieldOffset(FilterInvocation.class.getDeclaredField("listener_5"));
            } catch (Throwable err) {
                err.printStackTrace(System.err);
                throw new RuntimeException(err);
            }
            DST_OFT = s;
            DST_SZ = (e - s) / (FIELD_SZ - 1);
        }

        private final HttpExchange exchange;

        private int len;
        private Listener[] others;
        @SuppressWarnings("unused")
        Listener listener_0, listener_1, listener_2, listener_3, listener_4, listener_5;
        private Throwable bodyError;
        private boolean bodySentInvoked;

        FilterInvocation(HttpExchange exchange) {
            this.exchange = exchange;
        }

        void invokeBodySent(Throwable respErr) {
            bodySentInvoked = true;
            HttpExchange exchange = this.exchange;
            bodyError = respErr;
            Listener[] destroys = others;
            for (int i = len - 1; i >= 0; i--) {
                try {
                    if (i < 6) {
                        ((Listener) UnsafeUtil.getObject(this, DST_OFT + DST_SZ * i))
                                .onBodySent(exchange, respErr);
                    } else {
                        destroys[i - FIELD_SZ].onBodySent(exchange, respErr);
                    }
                } catch (Throwable e) {
                    log.error("error invoke onBodySent", e);
                }
            }
        }

        void invokeHeaderSend(int status) {
            Listener[] destroys = others;
            HttpExchange exchange = this.exchange;
            for (int i = len - 1; i >= 0; i--) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onHeaderSend(exchange, status);
                } else {
                    destroys[i - FIELD_SZ].onHeaderSend(exchange, status);
                }
            }
        }

        void invokeClientClosed() {
            Listener[] destroys = others;
            HttpExchange exchange = this.exchange;
            for (int i = len - 1; i >= 0; i--) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onClientAbort(exchange);
                } else {
                    destroys[i - FIELD_SZ].onClientAbort(exchange);
                }
            }
        }

        private void add(Listener destroy) {
            if (!bodySentInvoked) {
                int len = this.len;
                if (len < FIELD_SZ) {
                    PlatformDependent.putObject(this, DST_OFT + DST_SZ * len, destroy);
                } else {
                    int olen = len - FIELD_SZ;
                    if (others == null) {
                        others = new Listener[8];
                    } else if (olen >= others.length) {
                        others = Arrays.copyOf(others, olen << 2);
                    }
                    others[olen] = destroy;
                }
                this.len++;
            } else {
                destroy.onBodySent(exchange, bodyError);
            }
        }
    }

    public static final int STATE_CLOSE_REQ = 499;
    public static final int STATE_ERR_RESP_BODY = 498;
    public static final int STATE_CLOSE_RESP_BODY = 497;
    public static final int STATE_ERR_FLUSH_RESP = 496;
    public static final FiberException CLOSE_RESP = new FiberException("client closed connection prematurely before response header sending",
            STATE_CLOSE_REQ, "CLOSE_RESP");
    public static final FiberException CLOSE_RESP_BODY = new FiberException("client closed connection prematurely",
            STATE_CLOSE_RESP_BODY, "CLOSE_RESP_BODY");
    public static final FiberException ERROR_BODY_SIZE = new FiberException("client closed connection prematurely",
            495, "ERROR_BODY_SIZE");

    protected final FilterInvocation invocation = new FilterInvocation(this);
    private Object[] attrs;

    public final void addListener(Listener onDestroy) {
        if (onDestroy != null) {
            invocation.add(onDestroy);
            if (isClientClosed()) {
                onDestroy.onClientAbort(this);
            }
        }
    }

    public abstract String getPath();

    public abstract String getQuery();

    public abstract String getUri();

    public abstract void setMaxReqBodySizeAndCheck(long maxReqBodyLength) throws FiberException;

    public abstract void checkMaxReqBodySize() throws FiberException;

    /**
     * remote address
     */
    public abstract SocketAddress getRemoteAddress();

    /**
     * local address
     */
    public abstract SocketAddress getLocalAddress();

    public abstract void setRequestHeader(String name, String value);

    public abstract void addRequestHeader(String name, String value);

    public abstract String getRequestHeader(String name);

    public abstract List<String> getRequestHeaderList(String name);

    public abstract String getRequestHeader(CharSequence name);

    public abstract List<String> getRequestHeaderList(CharSequence name);

    public abstract Collection<String> getRequestHeaderNames();

    public abstract void setResponseHeader(String name, String value);

    public abstract void setResponseHeaderUnsafe(CharSequence name, CharSequence value);

    public abstract void setResponseHeader(String name, List<String> values);

    public abstract void addResponseHeader(String name, String value);

    public abstract void addResponseHeaderUnsafe(CharSequence name, CharSequence value);

    public abstract void addResponseHeader(String name, List<String> values);

    public abstract void removeResponseHeader(String name);

    public abstract String getResponseHeader(String name);

    public abstract List<String> getResponseHeaderList(String name);

    public abstract Collection<String> getResponseHeaderNames();

    public abstract HttpMethod getRequestMethod();

    public abstract void writeJson(int status, Object result);

    public abstract void writeRawBytes(int status, ByteBuf buf);

    public abstract void writeFileRegion(int status, FileRegion fileRegion);

    public void writeRawBytes(int status, Observable<ByteBuf> bufOb) {
        writeRawBytes(status, bufOb, Long.MAX_VALUE, false);
    }

    public void writeRawBytes(int status, Observable<ByteBuf> bufOb, long predicatedLength) {
        writeRawBytes(status, bufOb, predicatedLength, false);
    }

    public void writeRawBytes(int status, Observable<ByteBuf> bufOb, boolean flush) {
        writeRawBytes(status, bufOb, Long.MAX_VALUE, flush);
    }


    public abstract void writeRawBytes(int status, Observable<ByteBuf> bufOb, long predicatedLength, boolean flush);

    public abstract UpgradedConnection upgrade(int status, CharSequence protocol, long timeout);

    public abstract boolean isResponseWrote();

    public abstract void discardReqBody();

    public abstract int getWroteStatus();

    public abstract int getRecvReqBodyLen();

    public abstract long getSentRespBodyLen();

    public Observable<ByteBuf> readBody() {
        return readBodyUnsafe().notifyOn(Scheduler.current());
    }

    public Observable<ByteBuf> readBody(Scheduler scheduler) {
        return readBodyUnsafe().notifyOn(scheduler);
    }

    /**
     * this returned value must be subscribed
     *
     * @return ob
     */
    public abstract Observable<ByteBuf> peekBody();

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

    public abstract boolean isClientClosed();
}
