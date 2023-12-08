package io.fiber.net.common.ext;

import io.fiber.net.common.HttpExchange;

public interface RouterNameFetcher {
    String DEF_ROUTER_NAME = "fiber-net";

    RouterNameFetcher DEF_NAME = httpExchange -> DEF_ROUTER_NAME;

    String fetchName(HttpExchange httpExchange);
}
