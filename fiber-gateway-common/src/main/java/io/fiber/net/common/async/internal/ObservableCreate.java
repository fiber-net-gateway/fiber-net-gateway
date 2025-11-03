package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public final class ObservableCreate<T> implements Observable<T> {
    private final OnSubscribe<T> source;
    final Consumer<? super T> onDismiss;

    public ObservableCreate(OnSubscribe<T> source, Consumer<? super T> onDismiss) {
        this.source = source;
        this.onDismiss = onDismiss;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObE<T> emitter = new ObE<>(observer, onDismiss);
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
        private final Consumer<? super T> onDismiss;


        ObE(Observer<? super T> observer, Consumer<? super T> onDismiss) {
            this.observer = observer;
            this.onDismiss = onDismiss;
        }

        @Override
        public void onNext(T value) {
            if (!isDisposed()) {
                observer.onNext(value);
            } else {
                try {
                    onDismiss.accept(value);
                } catch (Throwable ignore) {
                }
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
