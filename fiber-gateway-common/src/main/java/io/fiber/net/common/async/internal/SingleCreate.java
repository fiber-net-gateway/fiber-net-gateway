package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public final class SingleCreate<T> implements Single<T> {

    final OnSubscribe<T> source;

    public SingleCreate(OnSubscribe<T> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObE<T> obE = new ObE<>(observer);
        observer.onSubscribe(obE);
        try {
            source.subscribe(obE);
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            obE.onError(e);
        }
    }

    static class ObE<T> extends DisposableOb implements Emitter<T> {
        private final Observer<? super T> observer;

        ObE(Observer<? super T> observer) {
            this.observer = observer;
        }

        @Override
        public void onSuccess(T t) {
            if (isDisposed()) {
                return;
            }
            try {
                observer.onSuccess(t);
            } finally {
                dispose();
            }

        }

        @Override
        public void onError(Throwable t) {
            if (isDisposed()) {
                return;
            }

            try {
                observer.onError(t);
            } finally {
                dispose();
            }

        }
    }
}