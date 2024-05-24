package io.fiber.net.http;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.async.internal.SingleSubject;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.Headers;
import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.PoolConfig;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientExchange {

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

        public void set(ClientExchange input, T value) {
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
        public T get(ClientExchange input) {
            Object[] arr;
            int idx;
            if ((arr = input.attrs) == null || (idx = this.id) >= arr.length) {
                return null;
            }
            return (T) arr[idx];
        }

        @SuppressWarnings("unchecked")
        public T remove(ClientExchange input) {
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
        default void onStart(ClientExchange exchange) {
        }

        default void onConnected(ClientExchange exchange) {
        }

        default void onResponse(ClientResponse response) {
        }

        default void onError(ClientExchange exchange, Throwable err) {
        }

        default void onComplete(ClientExchange exchange) {
        }
    }

    private static final class Invocation {
        private static final int FIELD_SZ = 6;
        private static final long DST_OFT;
        private static final long DST_SZ;
        // optimize memory alloc

        static {
            long s, e;
            try {
                s = PlatformDependent.objectFieldOffset(Invocation.class.getDeclaredField("listener_0"));
                e = PlatformDependent.objectFieldOffset(Invocation.class.getDeclaredField("listener_5"));
            } catch (Throwable err) {
                err.printStackTrace(System.err);
                throw new RuntimeException(err);
            }
            DST_OFT = s;
            DST_SZ = (e - s) / (FIELD_SZ - 1);
        }

        private final ClientExchange exchange;

        private int len;
        private Listener[] others;
        @SuppressWarnings("unused")
        Listener listener_0, listener_1, listener_2, listener_3, listener_4, listener_5;

        Invocation(ClientExchange exchange, Listener listener) {
            this.exchange = exchange;
            this.listener_0 = listener;
            len = 1;
        }

        void invokeStart() {
            ClientExchange ce = exchange;
            Listener[] listeners = others;
            for (int i = 0, len = this.len; i < len; i++) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onStart(ce);
                } else {
                    listeners[i - FIELD_SZ].onStart(ce);
                }
            }
        }

        void invokeConnected() {
            ClientExchange ce = exchange;
            Listener[] listeners = others;
            for (int i = 0, len = this.len; i < len; i++) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onConnected(ce);
                } else {
                    listeners[i - FIELD_SZ].onConnected(ce);
                }
            }
        }

        void invokeResponse(ClientResponse response) {
            Listener[] listeners = others;
            for (int i = 0, len = this.len; i < len; i++) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onResponse(response);
                } else {
                    listeners[i - FIELD_SZ].onResponse(response);
                }
            }
        }

        void invokeError(Throwable error) {
            ClientExchange ce = exchange;
            Listener[] listeners = others;
            for (int i = 0, len = this.len; i < len; i++) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onError(ce, error);
                } else {
                    listeners[i - FIELD_SZ].onError(ce, error);
                }
            }
        }

        void invokeCompleted() {
            ClientExchange ce = exchange;
            Listener[] listeners = others;
            for (int i = 0, len = this.len; i < len; i++) {
                if (i < 6) {
                    ((Listener) PlatformDependent.getObject(this, DST_OFT + DST_SZ * i))
                            .onComplete(ce);
                } else {
                    listeners[i - FIELD_SZ].onComplete(ce);
                }
            }
        }

        private void add(Listener listener) {
            int len = this.len;
            if (len < FIELD_SZ) {
                PlatformDependent.putObject(this, DST_OFT + DST_SZ * len, listener);
            } else {
                int olen = len - FIELD_SZ;
                if (others == null) {
                    others = new Listener[8];
                } else if (olen >= others.length) {
                    others = Arrays.copyOf(others, olen << 2);
                }
                others[olen] = listener;
            }
            this.len++;
        }
    }

    private Object[] attrs;
    private Invocation invocation;

    private final ConnectionPool connectionPool;
    private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
    protected HttpHost host;
    protected String uri = "/";
    protected HttpMethod method = HttpMethod.GET;
    protected int connectTimeout = PoolConfig.DEF_CONNECT_TIMEOUT;
    protected long maxBodyLength = PoolConfig.DEF_MAX_BODY_SIZE;
    protected int requestTimeout = PoolConfig.DEF_REQUEST_TIMEOUT;
    protected Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc;
    protected Function<ClientExchange, ByteBuf> reqBufFullFunc;
    protected boolean flushRequest;
    private ResponseOb responseOb;
    private boolean userSetHost;

    protected ClientExchange(ConnectionPool connectionPool, HttpHost host) {
        this.connectionPool = connectionPool;
        this.host = host;
        headers.set(HttpHeaderNames.HOST, host.getHostText());
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(io.fiber.net.common.HttpMethod method) {
        this.method = method;
    }

    public final void addHeader(String name, String value) {
        Boolean b = Headers.getHopHeaders(name);
        if (b == null) {
            headers.add(name, value);
            return;
        }
        if (b) {
            return;
        }
        userSetHost = true;
        headers.set(HttpHeaderNames.HOST, value);
    }

    public final void addHeader(String name, List<String> values) {
        if (CollectionUtils.isNotEmpty(values)) {
            Boolean b = Headers.getHopHeaders(name);
            if (b == null) {
                headers.add(name, values);
                return;
            }
            if (b) {
                return;
            }
            userSetHost = true;
            headers.set(HttpHeaderNames.HOST, values.get(0));
        }
    }

    public final void addListener(Listener listener) {
        if (invocation == null) {
            invocation = new Invocation(this, listener);
        } else {
            invocation.add(listener);
        }
    }

    public final void removeHeader(String name) {
        Boolean b = Headers.getHopHeaders(name);
        if (b == null) {
            headers.remove(name);
            return;
        }
        if (b || !userSetHost) {
            return;
        }
        userSetHost = false;
        headers.set(HttpHeaderNames.HOST, host.getHostText());
    }

    public final void setHeader(String name, String value) {
        Boolean b = Headers.getHopHeaders(name);
        if (b == null) {
            headers.set(name, value);
            return;
        }
        if (b) {
            return;
        }
        userSetHost = true;
        headers.set(HttpHeaderNames.HOST, value);
    }

    public final String getHeader(String name) {
        return headers.get(name);
    }

    public final List<String> getHeaderList(String name) {
        return headers.getAll(name);
    }

    public final Collection<String> getHeaderNames() {
        return headers.names();
    }

    public final void resetHost(HttpHost httpHost) {
        host = httpHost;
        if (!userSetHost) {
            headers.set(HttpHeaderNames.HOST, httpHost.getHostText());
        }
    }

    public final HttpHost getHost() {
        return host;
    }

    HttpHeaders headers() {
        return headers;
    }

    public void setReqBodyFunc(Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc, boolean flush) {
        flushRequest = flush;
        this.reqBufFullFunc = null;
        this.reqBodyFunc = reqBodyFunc;
    }

    public void setReqBufFullFunc(Function<ClientExchange, ByteBuf> reqBufFullFunc) {
        this.reqBodyFunc = null;
        flushRequest = false;
        this.reqBufFullFunc = reqBufFullFunc;
    }

    public boolean isRequestHeaderSent() {
        ResponseOb ob = responseOb;
        return ob != null && ob.isHeaderSent();
    }

    public boolean isRequestBodySent() {
        ResponseOb ob = responseOb;
        return ob != null && ob.isBodySent();
    }

    public boolean isResponseHeaderReceived() {
        ResponseOb ob = responseOb;
        return ob != null && ob.isHeaderReceived();
    }

    public Single<ClientResponse> sendForResp() {
        return sendForResp(null);
    }

    /**
     * must be release();
     *
     * @return ob
     */
    public Single<ClientResponse> sendForResp(Scheduler scheduler) {
        Invocation ivc = invocation;
        if (ivc != null) {
            ivc.invokeStart();
        }
        responseOb = new ResponseOb(this, scheduler);
        connectionPool.getConn(responseOb);
        return responseOb.single;
    }

    private static class RespSingle extends SingleSubject<ClientResponse> {

        @Override
        protected void onDismissClear(ClientResponse value) {
            value.discardRespBody();
        }
    }

    private static class ResponseOb extends ExchangeOb {
        private final RespSingle single;
        private final Scheduler scheduler;

        ResponseOb(ClientExchange exchange, Scheduler scheduler) {
            super(exchange);
            single = new RespSingle();
            this.scheduler = scheduler == null ? Scheduler.current() : scheduler;
        }

        @Override
        protected void onNotifyResp() {
            if (scheduler.inLoop()) {
                notifyResponse0();
            } else {
                scheduler.execute(this::notifyResponse0);
            }
        }

        private void notifyResponse0() {
            single.onSuccess(this);
            Invocation ivc = exchange.invocation;
            if (ivc != null) {
                ivc.invokeResponse(this);
            }
        }

        @Override
        protected void onNotifyError(Throwable err) {
            if (scheduler.inLoop()) {
                notifyError0(err);
            } else {
                scheduler.execute(() -> notifyError0(err));
            }
        }

        @Override
        protected void onConnected() {
            // not use scheduler
            Invocation ivc = exchange.invocation;
            if (ivc != null) {
                ivc.invokeConnected();
            }
        }

        private void notifyError0(Throwable err) {
            single.onError(err);
            Invocation ivc = exchange.invocation;
            if (ivc != null) {
                ivc.invokeError(err);
            }
        }

        @Override
        public ClientExchange getExchange() {
            return exchange;
        }

        @Override
        protected void onBodyCompleted() {
            Invocation ivc = exchange.invocation;
            if (ivc != null) {
                if (scheduler.inLoop()) {
                    ivc.invokeCompleted();
                } else {
                    scheduler.execute(ivc::invokeConnected);
                }
            }
        }

        @Override
        protected void onBodyError(Throwable throwable) {
            Invocation ivc = exchange.invocation;
            if (ivc != null) {
                if (scheduler.inLoop()) {
                    ivc.invokeError(throwable);
                } else {
                    scheduler.execute(() -> ivc.invokeError(throwable));
                }
            }
        }
    }

}
