package io.fiber.net.http;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.Headers;
import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.HttpConnection;
import io.fiber.net.http.impl.PoolConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientExchange {
    public static final HttpClientException CLIENT_ABORT = new HttpClientException("client abort http request", 499, "HTTP_CLIENT_ABORT");
    public static final HttpClientException CLIENT_ABORT_BODY = new HttpClientException("client abort http response body reading", 495, "HTTP_CLIENT_ABORT_BODY");

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

    public interface ConnListener {

        default void onConnected(ClientExchange exchange, HttpConnection conn) {
        }

        default void onDismiss(ClientExchange exchange, HttpConnection conn) {
        }
    }

    private Object[] attrs;
    private int lsLen;
    private ConnListener[] listeners;


    private final ConnectionPool connectionPool;
    private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
    protected HttpHost host;
    protected String uri = "/";
    protected HttpMethod method = HttpMethod.GET;
    protected int connectTimeout = PoolConfig.DEF_CONNECT_TIMEOUT;
    protected long maxBodyLength = PoolConfig.DEF_MAX_BODY_SIZE;
    protected int requestTimeout = PoolConfig.DEF_REQUEST_TIMEOUT;
    protected int upgradeConnTimeout = PoolConfig.DEF_UPGRADE_CONN_TIMEOUT;
    protected boolean upgradeAllowed;
    protected Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc;
    protected Function<ClientExchange, ByteBuf> reqBufFullFunc;
    protected Function<ClientExchange, FileRegion> reqFileFunc;
    protected boolean flushRequest;
    protected long predicatedBodySize = -1;
    private ExchangeOb responseOb;
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

    public void setUpgradeAllowed(boolean upgradeAllowed) {
        this.upgradeAllowed = upgradeAllowed;
    }

    public void setUpgradeConnTimeout(int upgradeConnTimeout) {
        this.upgradeConnTimeout = upgradeConnTimeout;
    }

    public void setMaxBodyLength(long maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
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

    public final void addListener(ConnListener listener) {
        ConnListener[] listeners = this.listeners;
        if (listeners == null) {
            listeners = this.listeners = new ConnListener[8];
        } else if (listeners.length <= lsLen) {
            listeners = this.listeners = Arrays.copyOf(listeners, lsLen << 1);
        }
        listeners[lsLen++] = listener;
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

    public final void setHeaderUnsafe(String name, String value) {
        headers.set(name, value);
    }

    public final void setHeaderUnsafe(CharSequence name, CharSequence value) {
        headers.set(name, value);
    }

    public final void addHeaderUnsafe(CharSequence name, CharSequence value) {
        headers.add(name, value);
    }

    public final void setHeader(String name, List<String> values) {
        if (CollectionUtils.isNotEmpty(values)) {
            Boolean b = Headers.getHopHeaders(name);
            if (b == null) {
                headers.set(name, values);
                return;
            }
            if (b) {
                return;
            }
            userSetHost = true;
            headers.set(HttpHeaderNames.HOST, values.get(0));
        } else {
            removeHeader(name);
        }
    }

    public final String getHeader(String name) {
        return headers.get(name);
    }

    public final String getHeader(CharSequence name) {
        return headers.get(name);
    }

    public final List<String> getHeaderList(String name) {
        return headers.getAll(name);
    }

    public final List<String> getHeaderList(CharSequence name) {
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
        setReqBodyFunc(reqBodyFunc, -1, flush);
    }

    public void setReqBodyFunc(Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc, long predicatedBodySize, boolean flush) {
        this.predicatedBodySize = predicatedBodySize;
        flushRequest = flush;
        this.reqBufFullFunc = null;
        this.reqFileFunc = null;
        this.reqBodyFunc = reqBodyFunc;
    }

    public void setReqBufFullFunc(Function<ClientExchange, ByteBuf> reqBufFullFunc) {
        this.reqBodyFunc = null;
        this.reqFileFunc = null;
        flushRequest = false;
        predicatedBodySize = -1;
        this.reqBufFullFunc = reqBufFullFunc;
    }

    public void setReqFileFunc(Function<ClientExchange, FileRegion> reqFileFunc) {
        this.reqFileFunc = reqFileFunc;
        flushRequest = false;
        predicatedBodySize = -1;
        this.reqBodyFunc = null;
        this.reqBufFullFunc = null;
    }

    public boolean isRequestHeaderSent() {
        ExchangeOb ob = responseOb;
        return ob != null && ob.isHeaderSent();
    }

    public boolean isRequestBodySent() {
        ExchangeOb ob = responseOb;
        return ob != null && ob.isBodySent();
    }

    public boolean isResponseHeaderReceived() {
        ExchangeOb ob = responseOb;
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
        responseOb = new ExchangeOb(this, scheduler);
        connectionPool.getConn(responseOb);
        return responseOb;
    }

    void invokeLsConnected(HttpConnection connection) {
        int lsLen;
        if ((lsLen = this.lsLen) > 0) {
            ConnListener[] listeners = this.listeners;
            for (int i = 0; i < lsLen; i++) {
                listeners[i].onConnected(this, connection);
            }
        }
    }

    void invokeLsDismiss(HttpConnection conn) {
        int lsLen;
        if ((lsLen = this.lsLen) > 0) {
            ConnListener[] listeners = this.listeners;
            for (int i = 0; i < lsLen; i++) {
                listeners[i].onDismiss(this, conn);
            }
        }
    }

}
