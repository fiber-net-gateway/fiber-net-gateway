package io.fiber.net.common.ext;

public interface RouterNameFetcher<E> {
    String DEF_ROUTER_NAME = "fiber-net";

    String fetchName(E exchange);
}
