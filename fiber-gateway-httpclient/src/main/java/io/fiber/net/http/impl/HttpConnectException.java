package io.fiber.net.http.impl;

import io.fiber.net.http.HttpClientException;

public class HttpConnectException extends HttpClientException {
    public static final String ERROR_NAME = "HTTP_CLIENT_CONNECT_ERROR";

    public HttpConnectException(String message) {
        this(message, 502, ERROR_NAME);
    }

    public HttpConnectException(String message, int code) {
        this(message, code, ERROR_NAME);
    }

    public HttpConnectException(String message, Throwable cause, int code) {
        this(message, cause, code, ERROR_NAME);
    }

    public HttpConnectException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public HttpConnectException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public HttpConnectException(Throwable cause, int code, String errorName) {
        super(cause, code, errorName);
    }
}
