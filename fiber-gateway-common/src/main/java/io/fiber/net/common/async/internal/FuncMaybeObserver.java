package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;

import java.util.function.BiConsumer;

public class FuncMaybeObserver<T> implements Maybe.Observer<T> {
    private Disposable disposable;
    private final java.util.function.BiConsumer<T, Throwable> consumer;
    private final Scheduler scheduler = Scheduler.current();

    public FuncMaybeObserver(BiConsumer<T, Throwable> consumer) {
        this.consumer = consumer;
    }

    public Disposable getDisposable() {
        return disposable;
    }

    @Override
    public void onSubscribe(Disposable d) {
        disposable = d;
    }

    @Override
    public void onSuccess(T t) {
        consumer.accept(t, null);
    }

    @Override
    public void onError(Throwable e) {
        consumer.accept(null, e);
    }

    @Override
    public void onComplete() {
        consumer.accept(null, null);
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }
}
