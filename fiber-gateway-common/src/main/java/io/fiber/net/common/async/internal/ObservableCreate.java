package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public final class ObservableCreate<T> implements Observable<T> {
    private final OnSubscribe<T> source;

    public ObservableCreate(OnSubscribe<T> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObE<T> emitter = new ObE<>(observer);
        observer.onSubscribe(emitter);
        try {
            source.subscribe(emitter);
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            emitter.onError(e);
        }
    }

    static class ObE<T> extends DisposableOb implements Emitter<T> {
        private final Observer<? super T> observer;

        ObE(Observer<? super T> observer) {
            this.observer = observer;
        }

        @Override
        public void onNext(T value) {
            if (!isDisposed()) {
                observer.onNext(value);
            }
        }

        @Override
        public void onError(Throwable error) {
            if (!isDisposed()) {
                try {
                    observer.onError(error);
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
