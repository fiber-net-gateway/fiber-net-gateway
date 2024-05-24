package io.fiber.net.http;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.netty.buffer.ByteBuf;

import java.util.Collection;
import java.util.List;

public interface ClientResponse {
    int status();

    String getHeader(String name);

    List<String> getHeaderList(String name);

    Collection<String> getHeaderNames();

    void discardRespBody();

    default Observable<ByteBuf> readRespBody() {
        return readRespBodyUnsafe().notifyOn(Scheduler.current());
    }

    default Observable<ByteBuf> readRespBody(Scheduler scheduler) {
        return readRespBodyUnsafe().notifyOn(scheduler);
    }

    /**
     * not notify cross thread. for performance.
     *
     * @return ob for body
     */
    Observable<ByteBuf> readRespBodyUnsafe();

    default Maybe<ByteBuf> readFullRespBody() {
        return readFullRespBody(Scheduler.current());
    }

    Maybe<ByteBuf> readFullRespBody(Scheduler scheduler);

    ClientExchange getExchange();

    int getReceivedBodySize();
}
