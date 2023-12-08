package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.utils.Exceptions;

public final class MaybeCreate<T> implements Maybe<T> {

    private final OnSubscribe<T> source;

    public MaybeCreate(OnSubscribe<T> source) {
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
            if (!isDisposed()) {
                try {
                    observer.onSuccess(t);
                } finally {
                    dispose();
                }
            }

        }

        @Override
        public void onError(Throwable t) {
            if (!isDisposed()) {
                try {
                    observer.onError(t);
                } finally {
                    dispose();
                }
            }
        }

        @Override
        public void onComplete() {
            if (!isDisposed()) {
                try {
                    observer.onComplete();
                } finally {
                    dispose();
                }
            }
        }
    }
}