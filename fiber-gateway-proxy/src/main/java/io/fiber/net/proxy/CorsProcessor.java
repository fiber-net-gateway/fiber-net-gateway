package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.server.HttpExchange;

public interface CorsProcessor {
    static boolean isPreflightReq(HttpExchange exchange, String origin) {
        return exchange.getRequestMethod() == HttpMethod.OPTIONS
                && StringUtils.isNotEmpty(origin)
                && StringUtils.isNotEmpty(exchange.getRequestHeader(ACCESS_CTL_REQ_MTD_HEADER));
    }

    static boolean isPreflightReq(HttpExchange exchange) {
        return isPreflightReq(exchange, exchange.getRequestHeader(ORIGIN_HEADER));
    }

    String ORIGIN_HEADER = "Origin";
    String ACCESS_CTL_REQ_MTD_HEADER = "access-control-request-method";

    boolean processCors(HttpExchange exchange);

}
