package io.fiber.net.http;

import io.fiber.net.common.async.*;
import io.fiber.net.common.async.internal.ConsumedSingle;
import io.fiber.net.common.codec.ChannelUpgradeConnection;
import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.utils.BodyBufSubject;
import io.fiber.net.http.impl.ClientHttpExchange;
import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.HttpConnection;
import io.netty.buffer.ByteBuf;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class ExchangeOb extends ClientHttpExchange implements ConnectionPool.ConnFetcher, ClientResponse, Single<ClientResponse>, Disposable {


    private static final HttpClientException CLIENT_ABORT = ClientExchange.CLIENT_ABORT;
    private static final HttpClientException CLIENT_ABORT_BODY = ClientExchange.CLIENT_ABORT_BODY;
    private static final HttpClientException FORBIDDEN = new HttpClientException("client cannot subscribe", 500, "ERROR_FORBIDDEN");
    private static final ConsumedSingle<ClientResponse> FORBIDDEN_SINGLE = new ConsumedSingle<>(FORBIDDEN);
    private static final ConsumedSingle<ClientResponse> CONSUMED_SINGLE = ConsumedSingle.getConsumed();
    private static final AtomicIntegerFieldUpdater<ExchangeOb> UPDATER = AtomicIntegerFieldUpdater.newUpdater(ExchangeOb.class, "requestState");
    //body_err  body_end   resp_error  resp_ended connected
    //    0         0          0          0        0
    //    1         1          1          1        1

    private static final int S_CONNECTING = 0;
    private static final int S_CONNECTED = 1;
    private static final int S_RESP_END = 2;
    private static final int S_RESP_ERROR = 4;
    private static final int S_BODY_END = 8; // 0b_01000
    private static final int S_BODY_ERR = 16; // 0b_10000
    // private static final int S_RESPONDED = S_CONNECTED | S_RESP_END; //0b_00011
    private static final int S_CONNECT_ERR = S_BODY_ERR | S_BODY_END | S_RESP_ERROR | S_RESP_END; // 0b_11110
    private static final int S_SOCKET_ERR = S_CONNECT_ERR | S_CONNECTED;//0b_11111
    // private static final int S_REQUEST_SUC = S_BODY_END | S_RESPONDED; // 0b_01011
    // private static final int S_REQUEST_ERR = S_SOCKET_ERR; // 0b_11111
    private static final int S_ABORT = 1 << 8;
    private static final int S_SUBSCRIBED = 1 << 9;
    private static final int MASK = S_ABORT - 1;

    final ClientExchange exchange;
    final Scheduler creator;
    private int respStatus;
    private HttpHeaders respHeaders;
    private boolean connUpgrade;
    private ChannelUpgradeConnection upgradeConn;
    private Throwable respError;
    private Observer<? super ClientResponse> observer;
    private boolean notified;
    volatile HttpConnection cn;
    private volatile int requestState = S_CONNECTING;

    ExchangeOb(ClientExchange exchange, Scheduler creator) {
        this.exchange = exchange;
        this.creator = creator == null ? Scheduler.current() : creator;
    }

    @Override
    public void onConnError(Throwable err) {
        requestErr = true;
        respError = err;
        setRespStateAndNotify(S_CONNECT_ERR);
    }

    @Override
    public void onConnSuccess(HttpConnection connection) {
        Function<ClientExchange, ByteBuf> reqBufFullFunc = exchange.reqBufFullFunc;
        Function<ClientExchange, FileRegion> reqFileFunc = exchange.reqFileFunc;
        Function<ClientExchange, Observable<ByteBuf>> reqBodyFunc = exchange.reqBodyFunc;

        try {
            exchange.invokeLsConnected(connection);
        } catch (Throwable e) {
            connection.dismiss();
            onSocketErr(e);
            return;
        }

        cn = connection;
        int s;
        do {
            s = UPDATER.get(this);
            if ((s & S_ABORT) != 0) {
                // abort on connection
                cn = null;
                connection.dismiss();
                exchange.invokeLsDismiss(connection);
                return;
            }
        } while (!UPDATER.compareAndSet(this, s, s | S_CONNECTED));

        if (reqBufFullFunc != null) {
            try {
                connection.send(this, reqBufFullFunc.invoke(exchange));
            } catch (Throwable e) {
                onSocketErr(e);
            }
        } else if (reqBodyFunc != null) {
            try {
                connection.send(this, reqBodyFunc.invoke(exchange), exchange.predicatedBodySize, exchange.flushRequest);
            } catch (Throwable e) {
                onSocketErr(e);
            }
        } else if (reqFileFunc != null) {
            try {
                connection.send(this, reqFileFunc.invoke(exchange));
            } catch (Throwable e) {
                onSocketErr(e);
            }
        } else {
            try {
                connection.sendNonBody(this);
            } catch (HttpClientException e) {
                onSocketErr(e);
            }
        }
    }

    private void dismissConn() {
        HttpConnection connection = cn;
        if (connection != null) {
            cn = null;
            assert connection.ioScheduler().inLoop();
            exchange.invokeLsDismiss(connection);
            connection.requestDismiss(this);
        }
    }

    @Override
    public int connectTimeout() {
        return exchange.connectTimeout;
    }

    @Override
    public HttpHost httpHost() {
        return exchange.host;
    }

    @Override
    protected HttpHeaders requestHeaders() {
        return exchange.headers();
    }

    @Override
    protected String requestUri() {
        return exchange.uri;
    }

    @Override
    protected io.netty.handler.codec.http.HttpMethod requestMethod() {
        return HttpMethod.valueOf(exchange.method.name());
    }

    @Override
    protected int requestTimeout() {
        return exchange.requestTimeout;
    }

    @Override
    protected long maxBodyLength() {
        return exchange.maxBodyLength;
    }

    @Override
    protected boolean isUpgradeAllowed() {
        return exchange.upgradeAllowed;
    }

    @Override
    protected void onBodyError(Throwable throwable) {
        dismissConn();
        int s;
        do {
            s = UPDATER.get(this);
        } while ((s & S_BODY_END) != 0 && !UPDATER.compareAndSet(this, s, s | (S_BODY_ERR | S_BODY_END)));
    }

    @Override
    protected void onBodyCompleted() {
        int s;
        do {
            s = UPDATER.get(this);
        } while ((s & S_BODY_END) != 0 && !UPDATER.compareAndSet(this, s, s | S_BODY_END));
        // invoke listener only. otherwise terminate request body sending
        HttpConnection connection = cn;
        if (connection != null) {
            cn = null;
            exchange.invokeLsDismiss(connection);
        }
    }

    @Override
    protected void onResp(int code, HttpHeaders headers, boolean cu) {
        respStatus = code;
        respHeaders = headers;
        connUpgrade = cu;
        headerReceived = true;
        HttpConnection hc;
        respBody = new BodyBufSubject((hc = cn).ioScheduler());
        if (cu) {
            upgradeConn = new ChannelUpgradeConnection(hc.getCh(), hc.ioScheduler(), exchange.upgradeConnTimeout);
        }
        setRespStateAndNotify(S_RESP_END);
    }

    private void setRespStateAndNotify(int state) {
        int s;
        do {
            s = UPDATER.get(this);
            if ((s & S_RESP_END) != 0) {
                return;
            }
        } while (!UPDATER.compareAndSet(this, s, s | state));

        if ((s & ~MASK) == S_SUBSCRIBED) {
            notifyResponse();
        }
    }

    @Override
    protected final void onSocketErr(Throwable err) {
        requestErr = true;
        respError = err;
        setRespStateAndNotify(S_SOCKET_ERR);
        dismissConn();
    }

    @Override
    public int status() {
        return respStatus;
    }

    @Override
    public String getHeader(String name) {
        return respHeaders.get(name);
    }

    @Override
    public String getHeader(CharSequence name) {
        return respHeaders.get(name);
    }

    @Override
    public List<String> getHeaderList(String name) {
        return respHeaders.getAll(name);
    }

    @Override
    public List<String> getHeaderList(CharSequence name) {
        return respHeaders.getAll(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return respHeaders.names();
    }

    @Override
    public void discardRespBody() {
        respBody.dismiss();
    }

    @Override
    public Observable<ByteBuf> readRespBodyUnsafe() {
        return respBody;
    }

    @Override
    public Maybe<ByteBuf> readFullRespBody(Scheduler scheduler) {
        if (respBody.getProducerScheduler() == scheduler) {
            return respBody.toMaybe();
        }
        return respBody.toMaybe().notifyOn(scheduler);
    }

    @Override
    public ClientExchange getExchange() {
        return exchange;
    }

    @Override
    public int getReceivedBodySize() {
        return respBody.getReceived();
    }

    @Override
    public boolean isUpgraded() {
        return connUpgrade;
    }

    @Override
    public void abortRespBody(Throwable cause) {
        if (isRequestEnd()) {
            return;
        }
        BodyBufSubject body;
        if ((body = respBody) == null) {
            throw new IllegalStateException("[bug] abort response body invoke before header receiving");
        }

        if (cause == null) {
            cause = CLIENT_ABORT_BODY;
        }

        Scheduler scheduler = body.getProducerScheduler();
        if (scheduler.inLoop()) {
            notifyError(cause, true);
        } else {
            Throwable finalCause = cause;
            scheduler.execute(() -> notifyError(finalCause, true));
        }
    }

    @Override
    public UpgradedConnection upgradeConnection() {
        return upgradeConn;
    }

    @Override
    public void discardBodyOrUpgrade() {
        discardRespBody();
        ChannelUpgradeConnection upgradeConn;
        if ((upgradeConn = this.upgradeConn) != null) {
            upgradeConn.discardData();
            upgradeConn.close();
        }
    }

    @Override
    public boolean dispose() {
        if (!creator.inLoop()) {
            return false;
        }

        int s;
        do {
            s = UPDATER.get(this);
            assert (s & S_SUBSCRIBED) == S_SUBSCRIBED;
            if ((s & S_ABORT) != 0) {
                return false;
            }
        } while (!UPDATER.compareAndSet(this, s, s | S_ABORT));

        if ((s & (S_BODY_END | S_BODY_ERR)) == 0) {
            // connection is using by this exchange
            // so web close the connection by notifying error
            HttpConnection connection = cn;
            if (connection != null) {
                Scheduler scheduler = connection.ioScheduler();
                if (scheduler.inLoop()) {
                    notifyError(CLIENT_ABORT, false);
                } else {
                    scheduler.execute(() -> notifyError(CLIENT_ABORT, false));
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean isDisposed() {
        return (requestState & S_ABORT) != 0;
    }

    @Override
    public void subscribe(Observer<? super ClientResponse> observer) {
        if (!creator.inLoop()) {
            FORBIDDEN_SINGLE.subscribe(observer);
            return;
        }
        if (this.observer != null) {
            CONSUMED_SINGLE.subscribe(observer);
            return;
        }
        this.observer = observer;
        int s;
        do {
            s = UPDATER.get(this);
            assert (s & (S_SUBSCRIBED | S_ABORT)) == 0;
        } while (!UPDATER.compareAndSet(this, s, s | S_SUBSCRIBED));

        observer.onSubscribe(this);

        if ((s & (S_RESP_ERROR | S_RESP_END)) != 0) {
            notifyResponse0(observer);
        }
    }

    private void notifyResponse() {
        Scheduler creator = this.creator;
        assert observer != null;
        if (creator.inLoop()) {
            notifyResponse0(observer);
        } else {
            creator.execute(() -> notifyResponse0(observer));
        }
    }

    private void notifyResponse0(Observer<? super ClientResponse> ob) {
        assert creator.inLoop();
        if (notified) {
            return;
        }
        notified = true;
        BodyBufSubject body = respBody;
        Throwable err = respError;
        if ((requestState & S_ABORT) != 0) {
            if (body != null) {
                body.dismiss();
            }
            return;
        }
        if (body != null) {
            ob.onSuccess(this);
        } else {
            ob.onError(err != null ? err : CLIENT_ABORT);
        }
    }
}