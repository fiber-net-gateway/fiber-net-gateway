package io.fiber.net.http.impl;

import io.fiber.net.http.HttpClientException;

public class HttpDnsException extends HttpClientException {
    public static final String ERROR_NAME = "HTTP_CLIENT_DNS_ERROR";

    public HttpDnsException(String message) {
        this(message, 503, ERROR_NAME);
    }

    public HttpDnsException(String message, int code) {
        this(message, code, ERROR_NAME);
    }

    public HttpDnsException(String message, Throwable cause, int code) {
        this(message, cause, code, ERROR_NAME);
    }

    public HttpDnsException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public HttpDnsException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public HttpDnsException(Throwable cause, int code, String errorName) {
        super(cause, code, errorName);
    }
}
