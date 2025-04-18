package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.SerializeJsonObservable;
import io.fiber.net.common.codec.ChannelUpgradeConnection;
import io.fiber.net.common.codec.ClosedConnection;
import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.utils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;

class HttpExchangeImpl extends HttpExchange implements UriCodec.Callback {
    private static final AsciiString APPLICATION_JSON_UTF8 = AsciiString.cached("application/json; charset=utf-8");
    static final FiberException TOO_LARGE_BODY_ERROR = new FiberException("request body is too large", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.code(), "REQ_BODY_TOO_LARGE");

    HttpExchangeImpl next;
    private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
    private final Channel ch;
    private final HttpRequest request;
    private final HttpMethod method;
    private final String uri;
    private final ReqHandler reqHandler;
    boolean ioError;
    boolean receiveCompleted;
    boolean clientClosed;
    boolean connUpgrade;
    boolean continue100;
    boolean prepareInvoke;
    boolean requestInvoked;
    boolean respEnd;

    private boolean responseWrote;
    private String path;
    private String query;
    private final BodyBufSubject reqBufSubject;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private long maxReqBodyLength;
    private boolean bodySizeChecked;
    private boolean bodyCheckErr;
    private long sentRespBodyLen = -1L;

    private int wroteStatus;

    public HttpExchangeImpl(HttpRequest request, ReqHandler reqHandler) {
        this.reqHandler = reqHandler;
        this.request = request;
        this.ch = reqHandler.getChannel();
        uri = request.uri();
        method = HttpMethod.valueOf(request.method().name());
        reqBufSubject = new BodyBufSubject(reqHandler.getScheduler());
        maxReqBodyLength = reqHandler.getDefaultMaxBodyLength();
        remoteAddress = ch.remoteAddress();
        localAddress = ch.localAddress();
        UriCodec.parseComplexUri(uri, this);
    }


    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public String getUri() {
        return uri;
    }


    @Override
    public void setMaxReqBodySizeAndCheck(long maxReqBodyLength) throws FiberException {
        this.maxReqBodyLength = maxReqBodyLength;
        if (doCheckBodySize()) {
            throw TOO_LARGE_BODY_ERROR;
        }
    }

    @Override
    public void checkMaxReqBodySize() throws FiberException {
        if (doCheckBodySize()) {
            throw TOO_LARGE_BODY_ERROR;
        }
    }

    boolean doCheckBodySize() {
        if (bodySizeChecked) {
            return bodyCheckErr;
        }
        bodySizeChecked = true;
        bodyCheckErr = reqHandler.checkBody(this, maxReqBodyLength);
        if (bodyCheckErr) {
            abortBody(TOO_LARGE_BODY_ERROR);
        }
        return bodyCheckErr;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public void setRequestHeader(String name, String value) {
        request.headers().set(name, value);
    }

    @Override
    public void addRequestHeader(String name, String value) {
        request.headers().add(name, value);
    }

    @Override
    public String getRequestHeader(String name) {
        return request.headers().get(name);
    }

    @Override
    public List<String> getRequestHeaderList(String name) {
        return request.headers().getAll(name);
    }

    @Override
    public String getRequestHeader(CharSequence name) {
        return request.headers().get(name);
    }

    @Override
    public List<String> getRequestHeaderList(CharSequence name) {
        return request.headers().getAll(name);
    }

    @Override
    public Collection<String> getRequestHeaderNames() {
        return request.headers().names();
    }

    @Override
    public void setResponseHeader(String name, String value) {
        if (!Headers.isHopHeaders(name)) {
            headers.set(name, value);
        }
    }

    @Override
    public void setResponseHeaderUnsafe(CharSequence name, CharSequence value) {
        headers.set(name, value);
    }

    @Override
    public void setResponseHeader(String name, List<String> values) {
        if (!Headers.isHopHeaders(name)) {
            headers.set(name, values);
        }
    }

    @Override
    public void addResponseHeader(String name, String value) {
        if (!Headers.isHopHeaders(name)) {
            headers.add(name, value);
        }
    }

    @Override
    public void addResponseHeaderUnsafe(CharSequence name, CharSequence value) {
        headers.add(name, value);
    }

    @Override
    public void addResponseHeader(String name, List<String> values) {
        if (!Headers.isHopHeaders(name)) {
            headers.add(name, values);
        }
    }

    @Override
    public void removeResponseHeader(String name) {
        if (!Headers.isHopHeaders(name)) {
            headers.remove(name);
        }
    }

    @Override
    public String getResponseHeader(String name) {
        return headers.get(name);
    }

    @Override
    public List<String> getResponseHeaderList(String name) {
        return headers.getAll(name);
    }

    @Override
    public Collection<String> getResponseHeaderNames() {
        return headers.names();
    }

    @Override
    public HttpMethod getRequestMethod() {
        return method;
    }

    @Override
    public void writeJson(int status, Object result) {
        if (responseWrote) {
            if (log.isDebugEnabled()) {
                log.debug("response is wrote");
            }
            return;
        }
        responseWrote = true;

        Channel ch = this.ch;
        if (!ch.isActive()) {
            invokeHeaderSent(status);
            onDestroy(CLOSE_RESP);
            return;
        }

        headers.set(HttpHeaderNames.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        new SerializeJsonObservable(result, ch.alloc()).subscribe(new RespWriteOb(false, Long.MAX_VALUE, status));
    }

    @Override
    public void writeRawBytes(int status, ByteBuf buf) {
        if (responseWrote) {
            buf.release();
            if (log.isDebugEnabled()) {
                log.debug("response is wrote");
            }
            return;
        }
        responseWrote = true;
        if (!ch.isActive()) {
            buf.release();
            invokeHeaderSent(status);
            onDestroy(CLOSE_RESP);
            return;
        }

        writeBody0(buf, status);
    }

    @Override
    public void writeFileRegion(int status, FileRegion fileRegion) {
        if (responseWrote) {
            fileRegion.release();
            if (log.isDebugEnabled()) {
                log.debug("response is wrote");
            }
            return;
        }
        responseWrote = true;
        Channel ch = this.ch;
        if (!ch.isActive()) {
            fileRegion.release();
            invokeHeaderSent(status);
            onDestroy(CLOSE_RESP);
            return;
        }

        long len = fileRegion.count() - fileRegion.transferred();
        if ((status == 0 || status == 200) && len == 0) {
            status = 204;
        }
        wroteStatus = status;
        sentRespBodyLen = len;

        ch.write(new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.valueOf(status), headerForSend(len)), ch.voidPromise());
        ch.write(fileRegion, ch.voidPromise());
        ChannelFuture f = ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (f.isDone()) {
            onResponseFlushed(f);
        } else {
            f.addListener(this::onResponseFlushed);
        }
    }

    @Override
    public void writeRawBytes(int status, Observable<ByteBuf> bufOb, long predicatedLength, boolean flush) {
        if (responseWrote) {
            if (log.isDebugEnabled()) {
                log.debug("response is wrote");
            }
            bufOb.subscribe(NoopBufObserver.INSTANCE);
            return;
        }
        responseWrote = true;
        if (!ch.isActive()) {
            bufOb.subscribe(NoopBufObserver.INSTANCE);
            invokeHeaderSent(status);
            onDestroy(CLOSE_RESP);
            return;
        }

        bufOb.subscribe(new RespWriteOb(flush, predicatedLength, status));
    }

    @Override
    public UpgradedConnection upgrade(int status, CharSequence protocol, long timeout) {
        if (responseWrote) {
            if (log.isDebugEnabled()) {
                log.debug("response is wrote");
            }
            return ClosedConnection.INSTANCE;
        }
        responseWrote = true;

        Channel ch = this.ch;
        if (!ch.isActive()) {
            invokeHeaderSent(status);
            onDestroy(CLOSE_RESP);
            return ClosedConnection.INSTANCE;
        }

        reqHandler.prepareUpgrade();

        DefaultHttpHeaders headers = this.headers;
        reqHandler.addConnectionHeader(headers);
        if (StringUtils.isNotEmpty(protocol)) {
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
            headers.set(HttpHeaderNames.UPGRADE, protocol);
        }
        invokeHeaderSent(status);
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(status),
                headers);

        ch.writeAndFlush(response, ch.newPromise().addListener(f -> {
            if (!receiveCompleted) {
                receiveCompleted = true;
                reqBufSubject.onComplete();
            }
            onResponseFlushed(f);
            if (f.isSuccess() && ch.isActive()) {
                reqHandler.upgrade();
            } else {
                ch.close();
            }
        }));
        return new ChannelUpgradeConnection(ch, reqBufSubject.getProducerScheduler(), timeout);
    }

    private void writeBody0(ByteBuf buf, int status) {
        if ((status == 0 || status == 200) && buf.readableBytes() == 0) {
            status = 204;
        }
        wroteStatus = status;
        sentRespBodyLen = buf.readableBytes();
        DefaultHttpHeaders headers = headerForSend(buf.readableBytes());
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.valueOf(status), buf, headers, EmptyHttpHeaders.INSTANCE);
        ChannelFuture f = ch.writeAndFlush(resp);
        if (f.isDone()) {
            onResponseFlushed(f);
        } else {
            f.addListener(this::onResponseFlushed);
        }
    }

    @Override
    public boolean isResponseWrote() {
        return responseWrote;
    }

    @Override
    public void discardReqBody() {
        doCheckBodySize();
        reqBufSubject.dismiss();
    }

    @Override
    public int getWroteStatus() {
        return wroteStatus;
    }

    @Override
    public int getRecvReqBodyLen() {
        return reqBufSubject.getReceived();
    }

    @Override
    public long getSentRespBodyLen() {
        return sentRespBodyLen;
    }

    @Override
    public Observable<ByteBuf> peekBody() {
        doCheckBodySize();
        return reqBufSubject.fork();
    }

    @Override
    public Observable<ByteBuf> readBodyUnsafe() {
        doCheckBodySize();
        return reqBufSubject;
    }

    @Override
    public Maybe<ByteBuf> readFullBody(Scheduler scheduler) {
        doCheckBodySize();
        if (reqBufSubject.getProducerScheduler() == scheduler) {
            return reqBufSubject.toMaybe();
        }
        return reqBufSubject.toMaybe().notifyOn(scheduler);
    }

    @Override
    public boolean isClientClosed() {
        return clientClosed;
    }

    void feedReqBody(ByteBuf buf, boolean last) {
        if (receiveCompleted) {
            buf.release();
            return;
        }
        reqBufSubject.onNext(buf);
        if (last) {
            receiveCompleted = true;
            reqBufSubject.onComplete();
        }
    }

    void abortBody(Throwable e) {
        if (receiveCompleted) {
            return;
        }
        receiveCompleted = true;
        ioError = true;
        if (!(e instanceof FiberException)) {
            e = new FiberException("error in receive req body", e, 400, "RECEIVE_REQ_BODY");
        }
        reqBufSubject.onError(e);
    }

    void onDestroy(Throwable bodyError) {
        if (!responseWrote) {
            log.warn("[bug]no response write after request ending");
        }
        respEnd = true;
        discardReqBody();
        invocation.invokeBodySent(bodyError);
        Scheduler scheduler = reqBufSubject.getProducerScheduler();
        if (scheduler.inLoop()) {
            reqHandler.invokeNext(this);
        } else {
            scheduler.execute(() -> reqHandler.invokeNext(this));
        }
    }

    @Override
    public void accept(String path, int argsStart, int argsEnd) {
        if (path != null) {
            this.path = path;
            if (argsEnd > 0) {
                this.query = uri.substring(argsStart, argsEnd);
            }
        } else {
            int i = uri.indexOf('?');
            if (i < 0) {
                this.path = uri;
            } else if (i == 0) {
                this.path = "/";
            } else {
                this.path = uri.substring(0, i);
                this.query = uri.substring(i + 1);
            }
        }
    }

    private class RespWriteOb implements Observable.Observer<ByteBuf> {
        private final Scheduler scheduler = reqBufSubject.getProducerScheduler();
        private final boolean flush;
        private final long predicatedLength;
        private boolean headerSent;
        private final HttpResponseStatus status;
        private Disposable disposable;
        private boolean abortNotify;

        public RespWriteOb(boolean flush, long predicatedLength, int status) {
            this.flush = flush;
            this.predicatedLength = predicatedLength >= 0 ? predicatedLength : Long.MAX_VALUE;
            this.status = HttpResponseStatus.valueOf(status);
            writeHeader(flush);
        }

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
            sentRespBodyLen = 0;
        }

        @Override
        public void onNext(ByteBuf buf) {
            if (!ch.isActive()) {
                buf.release();
                disposable.dispose();
                abortResponse();
                return;
            }
            if (buf.readableBytes() == 0) {
                buf.release();
                return;
            }
            if (scheduler.inLoop()) {
                writeBuf(buf);
            } else {
                scheduler.execute(() -> writeBuf(buf));
            }
        }

        private void abortResponse() {
            if (abortNotify) {
                return;
            }
            abortNotify = true;
            if (scheduler.inLoop()) {
                abortResponse0();
            } else {
                scheduler.execute(this::abortResponse0);
            }
        }

        private void abortResponse0() {
            if (!headerSent) {
                invokeHeaderSent(wroteStatus);
            }
            onDestroy(CLOSE_RESP_BODY);
        }

        private void writeBuf(ByteBuf buf) {
            if ((sentRespBodyLen += buf.readableBytes()) > predicatedLength) {
                ch.close();
                onDestroy(ERROR_BODY_SIZE);
                return;
            }
            if (flush) {
                ch.writeAndFlush(buf, ch.voidPromise());
            } else {
                ch.write(buf, ch.voidPromise());
            }
        }

        private void writeHeader(boolean flush) {
            if (!headerSent) {
                headerSent = true;
                wroteStatus = status.code();
                long contentLen = predicatedLength;
                if (contentLen == Long.MAX_VALUE) {
                    contentLen = -1L;
                }
                DefaultHttpResponse msg = new DefaultHttpResponse(request.protocolVersion(), status, headerForSend(contentLen));
                if (flush) {
                    ch.writeAndFlush(msg, ch.voidPromise());
                } else {
                    ch.write(msg, ch.voidPromise());
                }
            }
        }

        @Override
        public void onError(Throwable e) {
            if (scheduler.inLoop()) {
                onError0(e);
            } else {
                scheduler.execute(() -> onError0(e));
            }
        }

        private void onError0(Throwable e) {
            ChannelFuture f = ch.writeAndFlush(Unpooled.EMPTY_BUFFER);
            if (f.isDone()) {
                ch.close();
                onDestroy(new FiberException("sending response body aborted by error", e, STATE_ERR_RESP_BODY, "ERR_RESP_BODY"));
            } else {
                f.addListener(f1 -> {
                    ch.close();
                    onDestroy(new FiberException("sending response body aborted by error", e, STATE_ERR_RESP_BODY, "ERR_RESP_BODY"));
                });
            }
        }

        @Override
        public void onComplete() {
            if (!ch.isActive()) {
                abortResponse();
                return;
            }
            if (scheduler.inLoop()) {
                onComplete0();
            } else {
                scheduler.execute(this::onComplete0);
            }
        }

        private void onComplete0() {
            ChannelFuture f = ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (f.isDone()) {
                onResponseFlushed(f);
            } else {
                f.addListener(HttpExchangeImpl.this::onResponseFlushed);
            }
        }

    }

    private void onResponseFlushed(Future<?> f) {
        if (f.isSuccess()) {
            onDestroy(null);
        } else {
            onDestroy(new FiberException("error ocur in sending response", f.cause(), STATE_ERR_FLUSH_RESP, "ERROR_SEND_SERVER_RESP"));
        }
    }

    private void invokeHeaderSent(int status) {
        invocation.invokeHeaderSend(status);
    }

    void invokeClientClosed() {
        boolean b = clientClosed;
        clientClosed = true;
        if (!b && !respEnd) {
            invocation.invokeClientClosed();
        }
    }

    private DefaultHttpHeaders headerForSend(long contentLen) {
        DefaultHttpHeaders headers = this.headers;
        reqHandler.addConnectionHeader(headers);
        invokeHeaderSent(wroteStatus);
        if (ioError) {
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        if (headers.contains(HttpHeaderNames.CONTENT_LENGTH) || headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            return headers;
        }
        if (contentLen < 0) {
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        } else {
            headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLen);
        }
        return headers;
    }

}
