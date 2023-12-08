package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;

public class ErrMaybe implements Maybe<Object>, Disposable {
    private final Throwable err;

    public ErrMaybe(Throwable err) {
        this.err = err;
    }

    @Override
    public void subscribe(Observer<? super Object> observer) {
        observer.onSubscribe(this);
        observer.onError(err);
    }

    @Override
    public boolean isDisposed() {
        return true;
    }

    @Override
    public boolean dispose() {
        return false;
    }
}
