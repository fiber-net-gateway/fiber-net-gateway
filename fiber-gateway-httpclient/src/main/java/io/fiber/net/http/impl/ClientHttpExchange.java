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

    public final boolean isRequestEnd() {
        return requestSec || requestErr;
    }

    protected abstract HttpHeaders requestHeaders();

    protected abstract String requestUri();

    protected abstract HttpMethod requestMethod();

    protected abstract int requestTimeout();

    protected abstract long maxBodyLength();

    protected abstract boolean isUpgradeAllowed();

    protected abstract void onBodyError(Throwable throwable);

    protected abstract void onBodyCompleted();

    protected abstract void onResp(int code, HttpHeaders headers, boolean cu);

    protected abstract void onSocketErr(Throwable err);

    public final void notifyError(Throwable exception, boolean closeBody) {
        if (isRequestEnd()) {
            return;
        }
        HttpClientException hce;
        if (exception instanceof HttpClientException) {
            hce = (HttpClientException) exception;
        } else if (exception instanceof FiberException) {
            FiberException fe = (FiberException) exception;
            hce = new HttpClientException(fe.getMessage(), fe, fe.getCode(), fe.getErrorName());
        } else {
            hce = new HttpClientException(exception.getMessage(), exception, 502, "HTTP_CONNECTION_ERROR");
        }

        BodyBufSubject body = respBody;
        if (body == null) {
            requestErr = true;
            onSocketErr(hce);
        } else if (closeBody && !body.isCompleted()) {
            requestErr = true;
            body.onError(hce);
            onBodyError(hce);
        }
    }

    public void addBuf(ByteBuf buf, boolean last) {
        if (isRequestEnd()) {
            buf.release();
            return;
        }
        respBody.onNext(buf);
        if (last) {
            requestSec = true;
            respBody.onComplete();
            onBodyCompleted();
        }
    }

}
