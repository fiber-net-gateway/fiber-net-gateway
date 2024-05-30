package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public class ConsumedObservable<T> implements Observable<T>, Disposable {
    @SuppressWarnings("rawtypes")
    private static final ConsumedObservable INSTANCE = new ConsumedObservable();

    @SuppressWarnings("unchecked")
    public static <T> ConsumedObservable<T> getInstance() {
        return INSTANCE;
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