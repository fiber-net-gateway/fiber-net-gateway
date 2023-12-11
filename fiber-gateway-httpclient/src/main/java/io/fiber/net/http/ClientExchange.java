package io.fiber.net.http;

import io.fiber.net.common.async.BiConsumer;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.async.internal.SingleSubject;
import io.fiber.net.common.utils.Headers;
import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.HttpConnection;
import io.fiber.net.http.impl.PoolConfig;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

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

    private Object[] attrs;

    private final ConnectionPool connectionPool;
    protected final HostFetcher hostFetcher;
    private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
    protected String uri = "/";
    protected HttpMethod method = HttpMethod.GET;
    protected int connectTimeout = PoolConfig.DEF_CONNECT_TIMEOUT;
    protected long maxBodyLength = PoolConfig.DEF_MAX_BODY_SIZE;
    protected int requestTimeout = PoolConfig.DEF_REQUEST_TIMEOUT;
    protected Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc;
    protected Function<ClientExchange, ByteBuf> reqBufFullFunc;
    protected BiConsumer<ClientExchange, HttpConnection> peekConn;
    protected boolean flushRequest;
    private ResponseOb responseOb;

    protected ClientExchange(ConnectionPool connectionPool, HostFetcher hostFetcher) {
        this.connectionPool = connectionPool;
        this.hostFetcher = hostFetcher;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
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
        this.method = HttpMethod.valueOf(method.name());
    }

    public final void addHeader(String name, String value) {
        if (Headers.isHopHeaders(name)) {
            return;
        }
        headers.add(name, value);
    }

    public final void removeHeader(String name) {
        if (Headers.isHopHeaders(name)) {
            return;
        }
        headers.remove(name);
    }

    public final void setHeader(String name, String value) {
        if (Headers.isHopHeaders(name)) {
            return;
        }
        headers.set(name, value);
    }

    public final String getHeader(String name) {
        return headers.get(name);
    }

    public final List<String> getHeaderList(String name) {
        return headers.getAll(name);
    }

    public final Collection<String> getNames() {
        return headers.names();
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

    public void setPeekConn(BiConsumer<ClientExchange, HttpConnection> peekConn) {
        this.peekConn = peekConn;
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

    /**
     * must be release();
     *
     * @return ob
     */
    public Single<ClientResponse> sendForResp() {
        responseOb = new ResponseOb(this);
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
        private final RespSingle single = new RespSingle();

        ResponseOb(ClientExchange exchange) {
            super(exchange);
        }

        @Override
        protected void onNotifyResp() throws Throwable {
            single.onSuccess(this);
        }

        @Override
        protected void onNotifyError(Throwable err) {
            single.onError(err);
        }

        @Override
        public ClientExchange getExchange() {
            return exchange;
        }
    }

}
