package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public abstract class DisposableOb implements Disposable {
    private static final AtomicIntegerFieldUpdater<DisposableOb> UPDATER = AtomicIntegerFieldUpdater.newUpdater(
            DisposableOb.class, "disposed"
    );
    private volatile int disposed;

    @Override
    public boolean isDisposed() {
        return disposed != 0;
    }

    @Override
    public boolean dispose() {
        for (; ; ) {
            int i = UPDATER.get(this);
            if (i != 0) {
                return false;
            }
            if (UPDATER.compareAndSet(this, 0, 1)) {
                return true;
            }
        }
    }
}
