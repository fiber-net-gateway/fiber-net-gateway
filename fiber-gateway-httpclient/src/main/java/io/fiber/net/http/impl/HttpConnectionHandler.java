package io.fiber.net.http.impl;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.HttpClientException;
import io.fiber.net.http.HttpHost;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class HttpConnectionHandler extends HttpConnection implements ChannelInboundHandler, Runnable {
    private static final HttpClientException TIMEOUT = new HttpClientException("request timeout", 504, "REQUEST_TIMEOUT");
    private static final HttpClientException NO_RESPONSE = new HttpClientException("no response", 502, "HTTP_NO_RESPONSE");
    private static final HttpClientException ABORT_BODY = new HttpClientException("no response", 502, "HTTP_RESPONSE_ABORT");
    private static final HttpClientException ERROR_BODY_SIZE = new HttpClientException("predicated content length is not matched actual content length", 500, "HTTP_CLIENT_ERROR_BODY_SIZE");
    private final EventLoop eventLoop;
    private HttpClientCodec clientCodec;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> requestTimerFut;
    private final PoolConfig config;
    private Scheduler ioSch;
    private boolean chunkedBody;
    private long receivedBodyLength;
    private boolean connErr;
    private int requesting;
    private boolean closeByProto;
    private int requests;
    private long maxBodyLength;
    private boolean connUpgrade;

    public HttpConnectionHandler(Channel ch, HttpHost httpHost, EventLoop eventLoop, PoolConfig config) {
        super(ch, httpHost);
        this.eventLoop = eventLoop;
        this.config = config;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        assert eventLoop == ctx.executor();
        ctx.pipeline().addFirst(clientCodec = new HttpClientCodec(config.maxInlineLen, config.maxHeaderLen, config.maxChunkLen));
        if (getHttpHost().isSecure()) {
            SslContext context = SslContextBuilder.forClient()
                    .trustManager(config.trustManager)
                    .sslProvider(SslProvider.OPENSSL_REFCNT)
                    .build();
            ctx.pipeline().addFirst(context.newHandler(ctx.alloc()));
        }
        ioSch = Scheduler.current();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!isClosed()) {
            ClientHttpExchange exchange;
            if ((exchange = this.exchange) != null) {
                if (connUpgrade) {
                    exchange.addBuf(Unpooled.EMPTY_BUFFER, true);
                    this.exchange = null;
                }
                ioErrorAndClose(exchange.headerReceived ? ABORT_BODY : NO_RESPONSE);
            } else {
                close();
            }
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject) {
            DecoderResult decoderResult = ((HttpObject) msg).decoderResult();
            if (decoderResult.isFailure()) {
                ReferenceCountUtil.release(msg);
                Throwable cause = decoderResult.cause();
                ioErrorAndClose(new HttpClientException("reading response error:" + cause.getMessage(), cause, 502,
                        "READ_RESPONSE_ERROR"));
                return;
            }

            ClientHttpExchange exchange = this.exchange;
            if (exchange == null) {
                ReferenceCountUtil.release(msg);
                // no request handle??
                ioErrorAndClose(NO_RESPONSE);
            } else if (exchange.isRequestEnd()) {
                // maybe on response occur discard body.
                ReferenceCountUtil.release(msg);
                this.exchange = null;
            } else {
                boolean last = (msg instanceof LastHttpContent);
                if (last) {
                    clearRequesting(2);
                }
                onHttpMessage(msg, exchange, last);
            }
        } else if (connUpgrade) {
            // for
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
            close();
        }
    }

    private void clearRequesting(int bit) {
        requesting &= ~bit;
        if (requesting == 0) {
            if (connUpgrade) {
                this.exchange = null;
                isolate();
                clientCodec.upgradeFrom(ctx);
                ctx.pipeline().remove(this);
            } else {
                dismiss();
            }
        }
    }

    @Override
    public int getRequests() {
        return requests;
    }

    private void onHttpMessage(Object msg, ClientHttpExchange exchange, boolean last) {
        if (msg instanceof HttpResponse) {
            if (requestTimerFut != null) {
                requestTimerFut.cancel(false);
                requestTimerFut = null;
            }
            HttpResponse response = (HttpResponse) msg;
            closeByProto = closeByProto || !HttpUtil.isKeepAlive(response);
            long len = HttpUtil.getContentLength(response, -1L);
            chunkedBody = len == -1L;
            receivedBodyLength = 0;
            HttpHeaders headers = response.headers();
            boolean cu;
            closeByProto |= connUpgrade = cu = exchange.isUpgradeAllowed()
                    && headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true);

            if (cu) {
                clientCodec.prepareUpgradeFrom(ctx);
            }
            exchange.onResp(response.status().code(), headers, cu);

            if (maxBodyLength > 0L && len > maxBodyLength) {
                ReferenceCountUtil.release(msg);
                ioErrorAndClose(new HttpClientException("body size is too big：" + len, 500, "READ_RESP_BODY"));
                return;
            }
        }

        if (last || msg instanceof HttpContent) {
            ByteBuf buf = ((HttpContent) msg).content();
            if (chunkedBody && maxBodyLength > 0 && chunkedExceedMax(buf.readableBytes(), maxBodyLength)) {
                ReferenceCountUtil.release(msg);
                ioErrorAndClose(new HttpClientException("chunked body size is too big：" + receivedBodyLength, 500, "READ_RESP_BODY"));
                return;
            }
            exchange.addBuf(buf, last);
            return;
        }

        ReferenceCountUtil.release(msg);
    }

    private boolean chunkedExceedMax(long received, long maxBodyLen) {
        receivedBodyLength += received;
        return receivedBodyLength > maxBodyLen;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        getCh().attr(CONN_ATTR).set(null);
        ClientHttpExchange exchange;
        if (!connUpgrade && (exchange = this.exchange) != null) {
            ioErrorAndClose(exchange.headerReceived ? ABORT_BODY : NO_RESPONSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        boolean ioErr = cause instanceof IOException;
        if (exchange != null) {
            ioErrorAndClose(new HttpClientException("io error on http connection", cause, ioErr ? 502 : 500, "HTTP_IO_ERROR"));
        }
        if (!ioErr) {
            log.error("error in http client IO socket", cause);
        }
    }

    @Override
    protected boolean isValid() {
        return !connErr && requesting == 0 && !closeByProto;
    }

    HttpHeaders headerForSend(ClientHttpExchange exchange, long contentLength) {
        HttpHeaders headers = exchange.requestHeaders();
        if (!headers.contains(HttpHeaderNames.USER_AGENT)) {
            headers.set(HttpHeaderNames.USER_AGENT, config.userAgent);
        }
        String connection = headers.get(HttpHeaderNames.CONNECTION);
        int maxRequest;
        if (++requests >= (maxRequest = config.maxRequestPerConn) && maxRequest > 0
                && connection == null) {
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            closeByProto = true;
        } else if ("close".equalsIgnoreCase(connection)) {
            closeByProto = true;
        } else if ("upgrade".equalsIgnoreCase(connection)) {
            // upgrade ignore data;
            return headers;
        }

        if (headers.contains(HttpHeaderNames.CONTENT_LENGTH) || headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            return headers;
        }

        if (contentLength >= 0) {
            headers.set(HttpHeaderNames.CONTENT_LENGTH, Long.toString(contentLength));
        } else {
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        return headers;
    }

    @Override
    public void sendNonBody(ClientHttpExchange exchange) throws HttpClientException {
        onSend(exchange);
        ChannelFuture f = ctx.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                exchange.requestMethod(),
                exchange.requestUri(),
                Unpooled.EMPTY_BUFFER,
                headerForSend(exchange, 0),
                EmptyHttpHeaders.INSTANCE));

        if (f.isDone()) {
            onRequestSent(f);
        } else {
            f.addListener(this::onRequestSent);
        }
    }

    @Override
    public void send(ClientHttpExchange exchange, ByteBuf buf) throws HttpClientException {
        onSend(exchange);
        int length = buf.readableBytes();
        ChannelHandlerContext ctx = this.ctx;
        ChannelFuture hf = ctx.write(new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                exchange.requestMethod(),
                exchange.requestUri(),
                headerForSend(exchange, length)));
        ChannelFuture bf;
        if (length == 0) {
            buf.release();
            bf = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            bf = ctx.writeAndFlush(new DefaultLastHttpContent(buf, EmptyHttpHeaders.INSTANCE));
        }
        if (hf.isDone()) {
            onRequestHeaderSent(hf);
        } else {
            hf.addListener(this::onRequestHeaderSent);
        }

        if (bf.isDone()) {
            onRequestSent(bf);
        } else {
            bf.addListener(this::onRequestSent);
        }
    }

    @Override
    public void send(ClientHttpExchange exchange, FileRegion fileRegion) throws HttpClientException {
        onSend(exchange);
        ChannelHandlerContext ctx = this.ctx;
        ChannelFuture hf = ctx.write(new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                exchange.requestMethod(),
                exchange.requestUri(),
                headerForSend(exchange, fileRegion.count() - fileRegion.transferred())));
        ctx.write(fileRegion, ctx.voidPromise());
        ChannelFuture bf = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (hf.isDone()) {
            onRequestHeaderSent(hf);
        } else {
            hf.addListener(this::onRequestHeaderSent);
        }

        if (bf.isDone()) {
            onRequestSent(bf);
        } else {
            bf.addListener(this::onRequestSent);
        }
    }

    @Override
    public void send(ClientHttpExchange exchange, Observable<ByteBuf> buf, long predicatedLength, boolean flush) throws HttpClientException {
        onSend(exchange);
        ChannelHandlerContext ctx = this.ctx;
        ChannelFuture future = ctx.writeAndFlush(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                        exchange.requestMethod(),
                        exchange.requestUri(),
                        headerForSend(exchange, predicatedLength)),
                ctx.newPromise());
        if (future.isDone()) {
            if (!onRequestHeaderSent(future)) {
                return;
            }
        } else {
            future.addListener(HttpConnectionHandler.this::onRequestHeaderSent);
        }

        Ob ob = new Ob(exchange, predicatedLength, flush);
        buf.subscribe(ob);
    }

    @Override
    public Scheduler ioScheduler() {
        return ioSch;
    }

    private class Ob implements Observable.Observer<ByteBuf> {
        final ClientHttpExchange exchange;
        final boolean flush;
        final long predicatedLength;
        private long receivedLength;
        private Disposable d;

        private Ob(ClientHttpExchange exchange, long predicatedLength, boolean flush) {
            this.exchange = exchange;
            this.predicatedLength = predicatedLength;
            this.flush = flush;
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.d = d;
        }


        @Override
        public void onNext(ByteBuf buf) {
            if (!ctx.channel().isActive()) {
                buf.release();
                d.dispose();
                return;
            }
            long l;
            if ((l = buf.readableBytes()) == 0) {
                buf.release();
                return;
            }
            long predicatedLength;
            if ((predicatedLength = this.predicatedLength) >= 0 && ((receivedLength += l)) > predicatedLength) {
                buf.release();
                d.dispose();
                onError(ERROR_BODY_SIZE);
                return;
            }

            if (eventLoop.inEventLoop()) {
                sendBuf(buf);
            } else {
                eventLoop.execute(() -> sendBuf(buf));
            }
        }

        private void sendBuf(ByteBuf buf) {
            if (flush) {
                ctx.writeAndFlush(buf, ctx.voidPromise());
            } else {
                ctx.write(buf, ctx.voidPromise());
            }
        }

        @Override
        public void onError(Throwable e) {
            if (eventLoop.inEventLoop()) {
                ioErrorAndClose(e);
            } else {
                eventLoop.execute(() -> ioErrorAndClose(e));
            }
        }

        @Override
        public void onComplete() {
            if (eventLoop.inEventLoop()) {
                onComplete0();
            } else {
                eventLoop.execute(this::onComplete0);
            }
        }

        private void onComplete0() {
            ChannelFuture f = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (f.isDone()) {
                onRequestSent(f);
            } else {
                f.addListener(HttpConnectionHandler.this::onRequestSent);
            }
        }
    }

    @Override
    protected void onSend(ClientHttpExchange exchange) throws HttpClientException {
        super.onSend(exchange);
        if (requesting != 0) {
            throw new IllegalStateException("requesting");
        }
        requesting = 3;
        maxBodyLength = exchange.maxBodyLength();
    }

    private void onRequestSent(Future<? super Void> future) {
        if (future.isSuccess()) {
            if (exchange != null) {
                exchange.bodySent = exchange.headerSent = true;
                requestTimer(exchange.requestTimeout());
            }
            clearRequesting(1);
        } else {
            Throwable cause = future.cause();
            ioErrorAndClose(new HttpClientException("send request error:" + cause.getMessage(), cause, 502, "SEND_REQUEST_ERROR"));
        }
    }

    private boolean onRequestHeaderSent(Future<? super Void> future) {
        if (future.isSuccess()) {
            if (exchange != null) {
                exchange.headerSent = true;
            }
            return true;
        } else {
            Throwable cause = future.cause();
            ioErrorAndClose(new HttpClientException("send request header error:" + cause.getMessage(), cause, 502, "SEND_REQUEST_ERROR"));
            return false;
        }
    }

    private void requestTimer(long timeout) {
        if (timeout >= 0 && !exchange.headerReceived && !exchange.requestErr) {
            requestTimerFut = eventLoop.schedule(this, timeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        ioErrorAndClose(TIMEOUT);
    }

    private void ioErrorAndClose(Throwable exception) {
        connErr = true;
        ScheduledFuture<?> requestTimerFut = this.requestTimerFut;
        this.requestTimerFut = null;
        if (requestTimerFut != null && requestTimerFut.isCancellable()) {
            requestTimerFut.cancel(false);
        }

        ClientHttpExchange exchange = this.exchange;
        if (exchange != null) {
            this.exchange = null;
            exchange.notifyError(exception, true);
        }
        close();
    }
}
