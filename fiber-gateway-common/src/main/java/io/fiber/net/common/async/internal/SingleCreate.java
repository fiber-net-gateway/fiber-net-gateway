package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public final class SingleCreate<T> implements Single<T> {

    final OnSubscribe<T> source;
    final Consumer<? super T> onDismiss;

    public SingleCreate(OnSubscribe<T> source, Consumer<? super T> onDismiss) {
        this.source = source;
        this.onDismiss = onDismiss;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObE<T> obE = new ObE<>(observer, onDismiss);
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
        private final Consumer<? super T> onDismiss;

        ObE(Observer<? super T> observer, Consumer<? super T> onDismiss) {
            this.observer = observer;
            this.onDismiss = onDismiss;
        }

        @Override
        public void onSuccess(T t) {
            if (isDisposed()) {
                try {
                    onDismiss.accept(t);
                } catch (Throwable ignore) {
                }
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