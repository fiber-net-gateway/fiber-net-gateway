package io.fiber.net.common.async;

public interface Disposable {
    boolean isDisposed();

    boolean dispose();
}
