package io.fiber.net.common;

import io.fiber.net.common.utils.StringUtils;

public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, TRACE, OPTIONS, CONNECT;


    public static HttpMethod resolve(String methodTxt) {
        if (StringUtils.isEmpty(methodTxt)) {
            return null;
        }
        switch (methodTxt) {
            case "GET":
                return GET;
            case "POST":
                return POST;
            case "PUT":
                return PUT;
            case "DELETE":
                return DELETE;
            case "HEAD":
                return HEAD;
            case "TRACE":
                return TRACE;
            case "OPTIONS":
                return OPTIONS;
            case "PATCH":
                return PATCH;
            default:
                return null;
        }
    }

}
