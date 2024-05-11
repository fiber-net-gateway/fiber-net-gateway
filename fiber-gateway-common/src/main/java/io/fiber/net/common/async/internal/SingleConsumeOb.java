package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Single;

import java.util.function.BiConsumer;

public class SingleConsumeOb<T> implements Single.Observer<T> {
    private final java.util.function.BiConsumer<T, Throwable> com;
    private Disposable d;

    public SingleConsumeOb(BiConsumer<T, Throwable> com) {
        this.com = com;
    }

    public Disposable getD() {
        return d;
    }

    @Override
    public void onSubscribe(Disposable d) {
        this.d = d;
    }

    @Override
    public void onSuccess(T t) {
        com.accept(t, null);
    }

    @Override
    public void onError(Throwable e) {
        com.accept(null, e);
    }
}
