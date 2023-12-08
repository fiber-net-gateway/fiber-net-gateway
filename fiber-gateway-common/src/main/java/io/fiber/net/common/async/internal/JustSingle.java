package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Single;

public class JustSingle<T> implements Single<T>, Disposable {
    private final T value;

    public JustSingle(T value) {
        this.value = value;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        observer.onSubscribe(this);
        observer.onSuccess(value);
    }

    @Override
    public boolean isDisposed() {
        return false;
    }

    @Override
    public boolean dispose() {
        return false;
    }
}
