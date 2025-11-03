package io.fiber.net.common.ext;

import io.fiber.net.common.FiberException;

public interface Router<E> {
    RouterHandler<E> route(AbstractServer<E> server, E e) throws FiberException;
}
