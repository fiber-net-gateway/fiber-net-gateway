package io.fiber.net.http;

import io.fiber.net.common.FiberException;

public class HttpClientException extends FiberException {
    public HttpClientException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public HttpClientException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public HttpClientException(Throwable cause, int code, String errorName) {
        super(cause, code, errorName);
    }
}
