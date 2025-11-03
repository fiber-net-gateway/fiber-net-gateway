package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Single;

public class JustSingle<T> implements Single<T> {
    private final T value;
    private final Throwable err;

    public JustSingle(T value, Throwable err) {
        this.value = value;
        this.err = err;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        DisposableOb d = new SimpleDisposableOb();
        observer.onSubscribe(d);
        if (d.isDisposed()) {
            return;
        }
        if (err == null) {
            observer.onSuccess(value);
        } else {
            observer.onError(err);
        }
    }


}
