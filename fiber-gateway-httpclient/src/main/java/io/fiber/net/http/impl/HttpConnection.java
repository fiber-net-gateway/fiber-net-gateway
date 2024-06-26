package io.fiber.net.http.impl;

import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.HttpClientException;
import io.fiber.net.http.HttpHost;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpConnection {
    static final Logger log = LoggerFactory.getLogger(HttpConnection.class);

    protected static final int STATE_DETACH = 0;
    protected static final int STATE_POOLED = 1;
    protected static final int STATE_CLOSED = 2;

    static final AttributeKey<HttpConnection> CONN_ATTR = AttributeKey.valueOf("fiberNetConn");

    static HttpConnection ofConn(Channel ch) {
        return ch.attr(CONN_ATTR).get();
    }

    // linked node
    HttpConnection prev;
    HttpConnection next;
    ThreadConnHolder.ConnList connList;

    //
    private final HttpHost httpHost;
    private final Channel ch;

    private long lastUpdateTime = System.currentTimeMillis();

    private int connState;
    protected ClientHttpExchange exchange;

    public Channel getCh() {
        return ch;
    }

    public HttpConnection(Channel ch, HttpHost httpHost) {
        this.ch = ch;
        this.httpHost = httpHost;
        ch.attr(CONN_ATTR).set(this);
    }

    public final HttpHost getHttpHost() {
        return httpHost;
    }

    final void detached(ThreadConnHolder.ConnList connList) {
        assert this.connList == connList;
        connState = STATE_DETACH;
    }

    protected final void endRequest() {
        if (connState != STATE_DETACH) {
            close();
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
        if (isValid() && isActive() && connList.putHead(this)) {
            connState = STATE_POOLED;
        } else {
            close();
        }
    }

    public void close() {
        ThreadConnHolder.ConnList list = connList;
        if (list == null) {
            // closed
            return;
        }
        connList = null;
        if (connState == STATE_POOLED) {
            list.removeConn(this);
        }
        list.decrementTotal();
        connState = STATE_CLOSED;
        if (ch.isOpen()) {
            ch.close();
        }
        if (log.isDebugEnabled()) {
            log.debug("connection {} is closed", this);
        }
    }

    public boolean isClosed() {
        return connList == null;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void dismiss() {
        assert ch.eventLoop().inEventLoop();
        endRequest();
        exchange = null;
    }

    protected void onSend(ClientHttpExchange exchange) throws HttpClientException {
        assert connState == STATE_DETACH;
        assert connList.holder.executor.inEventLoop();
        this.exchange = exchange;
    }

    public boolean isActive() {
        return ch.isActive() && !isClosed();
    }

    protected abstract boolean isValid();


    public abstract void sendNonBody(ClientHttpExchange exchange) throws HttpClientException;

    public abstract void send(ClientHttpExchange exchange, ByteBuf buf) throws HttpClientException;

    public abstract void send(ClientHttpExchange exchange, Observable<ByteBuf> buf, boolean flush) throws HttpClientException;

    public abstract Scheduler ioScheduler();

    @Override
    public String toString() {
        return "HttpConnection(" + httpHost + "->" + ch.remoteAddress() + ")@" + hashCode();
    }
}
