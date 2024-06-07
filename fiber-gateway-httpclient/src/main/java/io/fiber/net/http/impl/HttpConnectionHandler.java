package io.fiber.net.http.impl;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.HttpClientException;
import io.fiber.net.http.HttpHost;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

class HttpConnectionHandler extends HttpConnection implements ChannelInboundHandler, Runnable {
    private static final HttpClientException TIMEOUT = new HttpClientException("request timeout", 504, "REQUEST_TIMEOUT");
    private static final HttpClientException NO_RESPONSE = new HttpClientException("no response", 502, "IO_ERROR");
    private final EventLoop eventLoop;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> requestTimerFut;
    private final PoolConfig config;
    private Scheduler ioSch;
    private boolean chunkedBody;
    private long receivedBodyLength;
    private boolean requesting;
    private boolean closeByProto;
    private int requests;
    private long maxBodyLength;

    public HttpConnectionHandler(Channel ch, HttpHost httpHost, EventLoop eventLoop, PoolConfig config) {
        super(ch, httpHost);
        this.eventLoop = eventLoop;
        this.config = config;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        assert eventLoop == ctx.executor();
        ctx.pipeline().addFirst(new HttpClientCodec(config.maxInlineLen, config.maxHeaderLen, config.maxChunkLen));
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
            if (exchange != null) {
                ioErrorAndClose(NO_RESPONSE);
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
        }

        ClientHttpExchange exchange = this.exchange;
        if (exchange == null) {
            ReferenceCountUtil.release(msg);
            // no request handle??
            ioErrorAndClose(NO_RESPONSE);
            return;
        }

        onHttpMessage(msg, exchange);
        if (exchange.isRequestEnd()) {
            this.exchange = null;
            dismiss();
        }
    }

    private void onHttpMessage(Object msg, ClientHttpExchange exchange) {
        if (msg instanceof HttpResponse) {
            if (requestTimerFut != null) {
                requestTimerFut.cancel(false);
                requestTimerFut = null;
            }
            chunkedBody = false;
            receivedBodyLength = 0;
            exchange.headerReceived = true;
            HttpResponse response = (HttpResponse) msg;
            closeByProto |= !HttpUtil.isKeepAlive(response);
            exchange.onResp(response.status().code(), response.headers());
            long len = HttpUtil.getContentLength(response, -1L);
            if (maxBodyLength > 0L && len > maxBodyLength) {
                ReferenceCountUtil.release(msg);
                ioErrorAndClose(new HttpClientException("body size is too big：" + len, 500, "READ_RESP_BODY"));
                return;
            } else if (len == -1L) {
                // chunked
                chunkedBody = true;
            }
        }

        // maybe on response occur discard body.
        if (exchange.isRequestEnd()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (msg instanceof HttpContent) {
            ByteBuf buf = ((HttpContent) msg).content();
            boolean last = msg instanceof LastHttpContent;
            if (last) {
                this.requesting = false;
            }
            if (chunkedBody && maxBodyLength > 0 && chunkedExceedMax(buf.readableBytes(), maxBodyLength)) {
                ReferenceCountUtil.release(msg);
                ioErrorAndClose(new HttpClientException("chunked body size is too big：" + receivedBodyLength, 500, "READ_RESP_BODY"));
                return;
            }

            if (last) {
                exchange.requestSec = true;
            }
            exchange.addBuf(buf, last);
        }
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

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!isClosed() && exchange != null) {
            ioErrorAndClose(new HttpClientException("io error on http connection", cause, 502, "IO_ERROR"));
        }
    }

    @Override
    protected boolean isValid() {
        return !requesting && !closeByProto;
    }

    HttpHeaders headerForSend(ClientHttpExchange exchange, int contentLength) {
        HttpHeaders headers = exchange.requestHeaders();
        if (!headers.contains(HttpHeaderNames.USER_AGENT)) {
            headers.set(HttpHeaderNames.USER_AGENT, config.userAgent);
        }

        int maxRequest;
        if ((maxRequest = config.maxRequestPerConn) > 0 && ++requests >= maxRequest) {
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            closeByProto = true;
        }
        if (contentLength >= 0) {
            headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        } else {
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        return headers;
    }

    @Override
    public void sendNonBody(ClientHttpExchange exchange) throws HttpClientException {
        send(exchange, Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void send(ClientHttpExchange exchange, ByteBuf buf) throws HttpClientException {
        onSend(exchange);
        requesting = true;
        HttpMethod method = exchange.requestMethod();
        String uri = exchange.requestUri();
        HttpHeaders headers = headerForSend(exchange, buf.readableBytes());
        ctx.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, buf, headers, EmptyHttpHeaders.INSTANCE),
                ctx.newPromise().addListener(this::onRequestSent));
    }

    @Override
    public void send(ClientHttpExchange exchange, Observable<ByteBuf> buf, boolean flush) throws HttpClientException {
        onSend(exchange);
        Ob ob = new Ob(exchange, flush);
        buf.subscribe(ob);
    }

    @Override
    public Scheduler ioScheduler() {
        return ioSch;
    }

    private class Ob implements Observable.Observer<ByteBuf> {
        final ClientHttpExchange exchange;
        final boolean flush;
        boolean headerSent;

        private Ob(ClientHttpExchange exchange, boolean flush) {
            this.exchange = exchange;
            this.flush = flush;
        }

        @Override
        public void onSubscribe(Disposable d) {
        }

        private void sendReq() {
            if (headerSent) {
                return;
            }
            headerSent = true;
            requesting = true;
            HttpMethod method = exchange.requestMethod();
            String uri = exchange.requestUri();
            HttpHeaders headers = headerForSend(exchange, -1);
            ctx.write(new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, uri, headers), ctx.newPromise().addListener(HttpConnectionHandler.this::onRequestHeaderSent));
        }


        @Override
        public void onNext(ByteBuf buf) {
            if (buf.readableBytes() == 0 || !ctx.channel().isActive()) {
                buf.release();
                return;
            }
            if (eventLoop.inEventLoop()) {
                sendBuf(buf);
            } else {
                eventLoop.execute(() -> sendBuf(buf));
            }
        }

        private void sendBuf(ByteBuf buf) {
            sendReq();
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
            sendReq();
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.newPromise()
                    .addListener(HttpConnectionHandler.this::onRequestBodySent));
        }
    }

    @Override
    protected void onSend(ClientHttpExchange exchange) throws HttpClientException {
        super.onSend(exchange);
        if (requesting) {
            throw new IllegalStateException("requesting");
        }
        maxBodyLength = exchange.maxBodyLength();
    }

    private void onRequestSent(Future<? super Void> future) {
        if (future.isSuccess()) {
            if (exchange != null) {
                exchange.bodySent = exchange.headerSent = true;
                requestTimer(exchange.requestTimeout());
            }
        } else {
            Throwable cause = future.cause();
            ioErrorAndClose(new HttpClientException("send request error:" + cause.getMessage(), cause, 502, "SEND_REQUEST_ERROR"));
        }
    }

    private void onRequestHeaderSent(Future<? super Void> future) {
        if (future.isSuccess()) {
            if (exchange != null) {
                exchange.headerSent = true;
            }
        } else {
            Throwable cause = future.cause();
            ioErrorAndClose(new HttpClientException("send request error:" + cause.getMessage(), cause, 502, "SEND_REQUEST_ERROR"));
        }
    }

    private void onRequestBodySent(Future<? super Void> future) {
        if (future.isSuccess()) {
            if (exchange != null) {
                exchange.bodySent = true;
                requestTimer(exchange.requestTimeout());
            }
        } else {
            Throwable cause = future.cause();
            ioErrorAndClose(new HttpClientException("send request error:" + cause.getMessage(), cause, 502, "SEND_REQUEST_ERROR"));
        }
    }

    private void requestTimer(long timeout) {
        if (timeout >= 0) {
            requestTimerFut = eventLoop.schedule(this, timeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        ioErrorAndClose(TIMEOUT);
    }

    private void ioErrorAndClose(Throwable exception) {
        ScheduledFuture<?> requestTimerFut = this.requestTimerFut;
        this.requestTimerFut = null;
        if (requestTimerFut != null && requestTimerFut.isCancellable()) {
            requestTimerFut.cancel(false);
        }

        ClientHttpExchange exchange = this.exchange;
        if (exchange != null) {
            this.exchange = null;
            if (!exchange.isRequestEnd()) {
                exchange.notifyError(exception);
            }
        }
        dismiss();
    }
}
