package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.SerializeJsonObservable;
import io.fiber.net.common.utils.BodyBufSubject;
import io.fiber.net.common.utils.Headers;
import io.fiber.net.common.utils.NoopBufObserver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;

class HttpExchangeImpl extends HttpExchange {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpExchangeImpl.class);
    private static final AsciiString APPLICATION_JSON_UTF8 = AsciiString.cached("application/json; charset=utf-8");


    private final DefaultHttpHeaders headers = new DefaultHttpHeaders();
    private final Channel ch;
    private final HttpRequest request;
    private final HttpMethod method;
    private final String uri;
    private boolean ioError;
    private boolean receiveCompleted;
    private boolean responseWrote;
    private final String path;
    private final String query;
    private final BodyBufSubject reqBufSubject;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private int wroteStatus;

    public HttpExchangeImpl(Channel ch, HttpRequest request, Scheduler scheduler) {
        this.ch = ch;
        this.request = request;
        uri = request.uri();
        method = HttpMethod.valueOf(request.method().name());
        reqBufSubject = new BodyBufSubject(scheduler);
        int i;
        if ((i = uri.indexOf('?')) != -1) {
            path = uri.substring(0, i);
            query = uri.substring(i + 1);
        } else {
            path = uri;
            query = null;
        }
        remoteAddress = ch.remoteAddress();
        localAddress = ch.localAddress();
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
    public void addResponseHeader(String name, String value) {
        if (!Headers.isHopHeaders(name)) {
            headers.add(name, value);
        }
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
            if (logger.isDebugEnabled()) {
                logger.debug("response is wrote");
            }
            return;
        }
        responseWrote = true;

        Channel ch = this.ch;
        if (!ch.isActive()) {
            invokeHeaderSent(status);
            onDestroy(STATE_CLOSE_REQ);
            return;
        }

        headers.set(HttpHeaderNames.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        new SerializeJsonObservable(result, ch.alloc()).subscribe(new RespWriteOb(false, status));
    }

    @Override
    public void writeRawBytes(int status, ByteBuf buf) {
        if (responseWrote) {
            buf.release();
            if (logger.isDebugEnabled()) {
                logger.debug("response is wrote");
            }
            return;
        }
        responseWrote = true;
        if (!ch.isActive()) {
            buf.release();
            invokeHeaderSent(status);
            onDestroy(STATE_CLOSE_REQ);
            return;
        }

        writeBody0(buf, status);
        onDestroy(wroteStatus);
    }

    @Override
    public void writeRawBytes(int status, Observable<ByteBuf> bufOb, boolean flush) {
        if (responseWrote) {
            if (logger.isDebugEnabled()) {
                logger.debug("response is wrote");
            }
            bufOb.subscribe(NoopBufObserver.INSTANCE);
            return;
        }
        responseWrote = true;
        if (!ch.isActive()) {
            bufOb.subscribe(NoopBufObserver.INSTANCE);
            invokeHeaderSent(status);
            onDestroy(STATE_CLOSE_REQ);
            return;
        }

        bufOb.subscribe(new RespWriteOb(flush, status));
    }

    private void writeBody0(ByteBuf buf, int status) {
        if ((status == 0 || status == 200) && buf.readableBytes() == 0) {
            status = 204;
        }
        wroteStatus = status;
        DefaultHttpHeaders headers = headerForSend(buf.readableBytes());
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.valueOf(status), buf, headers, EmptyHttpHeaders.INSTANCE);
        Channel ch = this.ch;
        ch.writeAndFlush(resp, ch.voidPromise());
    }

    @Override
    public boolean isResponseWrote() {
        return responseWrote;
    }

    @Override
    public void discardReqBody() {
        reqBufSubject.dismiss();
    }

    @Override
    public Observable<ByteBuf> readBodyUnsafe() {
        return reqBufSubject;
    }

    @Override
    public Maybe<ByteBuf> readFullBody(Scheduler scheduler) {
        if (reqBufSubject.getProducerScheduler() == scheduler) {
            return reqBufSubject.toMaybe();
        }
        return reqBufSubject.toMaybe().notifyOn(scheduler);
    }

    void feedReqBody(ByteBuf buf, boolean last) {
        if (receiveCompleted) {
            buf.release();
            return;
        }
        reqBufSubject.onNext(buf);
        if (last) {
            reqBufSubject.onComplete();
            receiveCompleted = true;
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

    void onDestroy(int state) {
        if (!responseWrote && ch.isActive()) {
            logger.warn("no response write after request ending");
            writeBody0(Unpooled.EMPTY_BUFFER, 204);
        }
        reqBufSubject.dismiss();
        FilterInvocation ivc = invocation;
        if (ivc != null) {
            ivc.invokeBodySent(state);
        }
    }

    private class RespWriteOb implements Observable.Observer<ByteBuf> {
        private final Scheduler scheduler = reqBufSubject.getProducerScheduler();
        private final boolean flush;
        private boolean headerSent;
        private HttpResponseStatus status;
        private Disposable disposable;
        private boolean abortNotify;

        public RespWriteOb(boolean flush, int status) {
            this.flush = flush;
            this.status = HttpResponseStatus.valueOf(status);
        }

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
            if (flush) {
                writeHeader(true);
            }
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
            onDestroy(STATE_CLOSE_RESP_BODY);
        }

        private void writeBuf(ByteBuf buf) {
            if (flush) {
                ch.writeAndFlush(buf, ch.voidPromise());
            } else {
                writeHeader(false);
                ch.write(buf, ch.voidPromise());
            }
        }

        private void writeHeader(boolean flush) {
            if (!headerSent) {
                headerSent = true;
                wroteStatus = status.code();
                DefaultHttpResponse msg = new DefaultHttpResponse(request.protocolVersion(), status, headerForSend(-1));
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
            if (!headerSent) {
                headerSent = true;
                ByteBuf buf = ch.alloc().buffer();
                int status = Outputs.errorResponse(e, buf);
                headers.set(HttpHeaderNames.CONTENT_TYPE, APPLICATION_JSON_UTF8);
                this.status = HttpResponseStatus.valueOf(status);
                writeBody0(buf, status);
            } else {
                ch.close();
            }
            onDestroy(STATE_ERR_RESP_BODY);
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
            writeHeader(false);
            ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ch.voidPromise());
            onDestroy(wroteStatus);
        }

    }

    private void invokeHeaderSent(int status) {
        FilterInvocation inc = invocation;
        if (inc != null) {
            inc.invokeHeaderSend(status);
        }
    }

    private DefaultHttpHeaders headerForSend(int contentLen) {
        DefaultHttpHeaders headers = this.headers;
        if (contentLen != 0 && !headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
            headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        }
        ReqHandler.addConnectionHeader(headers);
        invokeHeaderSent(wroteStatus);
        if (ioError) {
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        if (contentLen < 0) {
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        } else {
            headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLen);
        }
        return headers;
    }

}
