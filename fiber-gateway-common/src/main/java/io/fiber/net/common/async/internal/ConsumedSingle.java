package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public class ConsumedSingle<T> implements Single<T>, Disposable {
    private final Throwable err;
    @SuppressWarnings("rawtypes")
    private static final ConsumedSingle CONSUMED = new ConsumedSingle(Exceptions.OB_CONSUMED);

    public ConsumedSingle(Throwable err) {
        this.err = err;
    }

    @SuppressWarnings("unchecked")
    public static <T> ConsumedSingle<T> getConsumed() {
        return CONSUMED;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
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