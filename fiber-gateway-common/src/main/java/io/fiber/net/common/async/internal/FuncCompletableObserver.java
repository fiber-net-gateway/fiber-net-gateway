package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Disposable;

public class FuncCompletableObserver implements Completable.Observer {
    private Disposable disposable;
    private final java.util.function.Consumer<? super Throwable> consumer;

    public FuncCompletableObserver(java.util.function.Consumer<? super Throwable> consumer) {
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
    public void onError(Throwable e) {
        consumer.accept(e);
    }

    @Override
    public void onComplete() {
        consumer.accept(null);
    }

}
