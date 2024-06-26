package io.fiber.net.http.impl;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.utils.BodyBufSubject;
import io.fiber.net.http.HttpClientException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

public abstract class ClientHttpExchange {
    protected boolean headerSent;
    protected boolean bodySent;
    protected boolean headerReceived;
    protected boolean requestSec;
    protected boolean requestErr;
    protected BodyBufSubject respBody;

    public void reset() {
        requestSec = headerReceived = bodySent = headerSent = false;
    }

    public boolean isHeaderSent() {
        return headerSent;
    }

    public boolean isBodySent() {
        return bodySent;
    }

    public boolean isHeaderReceived() {
        return headerReceived;
    }

    public boolean isRequestEnd() {
        return requestSec || requestErr;
    }

    protected abstract HttpHeaders requestHeaders();

    protected abstract String requestUri();

    protected abstract HttpMethod requestMethod();

    protected abstract int requestTimeout();

    protected abstract long maxBodyLength();

    protected abstract void onBodyError(Throwable throwable);

    protected abstract void onBodyCompleted();

    protected abstract void onResp(int code, HttpHeaders headers);

    protected abstract void onSocketErr(Throwable err);

    public final void notifyError(Throwable exception) {
        if (requestErr) {
            return;
        }
        requestErr = true;

        if (!(exception instanceof FiberException)) {
            exception = new HttpClientException(exception.getMessage(), exception, 502, "HTTP_CONNECTION_ERROR");
        }

        if (respBody != null) {
            respBody.onError(exception);
            onBodyError(exception);
        } else {
            onSocketErr(exception);
        }
    }

    public void addBuf(ByteBuf buf, boolean last) {
        respBody.onNext(buf);
        if (last) {
            requestSec = true;
            respBody.onComplete();
            onBodyCompleted();
        }
    }
}
