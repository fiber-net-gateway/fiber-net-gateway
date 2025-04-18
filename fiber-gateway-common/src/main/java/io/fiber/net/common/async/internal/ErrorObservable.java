package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public class ErrorObservable<T> implements Observable<T>, Disposable {
    @SuppressWarnings("rawtypes")
    private static final ErrorObservable CONSUMED = new ErrorObservable(Exceptions.OB_CONSUMED);

    @SuppressWarnings("unchecked")
    public static <T> ErrorObservable<T> getConsumed() {
        return CONSUMED;
    }

    private final Throwable err;

    public ErrorObservable(Throwable err) {
        this.err = err;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        observer.onSubscribe(this);
        observer.onError(Exceptions.OB_CONSUMED);
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