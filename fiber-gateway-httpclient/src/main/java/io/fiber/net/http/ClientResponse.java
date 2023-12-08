package io.fiber.net.http;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.netty.buffer.ByteBuf;

import java.util.Collection;
import java.util.List;

public interface ClientResponse {
    int status();

    String getHeader(String name);

    List<String> getHeaderList(String name);

    Collection<String> getHeaderNames();

    void discardRespBody();

    Observable<ByteBuf> readRespBody();

    Maybe<ByteBuf> readFullRespBody();

    ClientExchange getExchange();
}
