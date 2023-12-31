package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Scheduler;
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
        private final Scheduler scheduler;

        ObE(Observer<? super T> observer) {
            this.observer = observer;
            scheduler = observer.scheduler();
        }

        @Override
        public void onSuccess(T t) {
            if (isDisposed()) {
                return;
            }
            Scheduler scheduler = this.scheduler;
            try {
                if (scheduler.inLoop()) {
                    observer.onSuccess(t);
                } else {
                    scheduler.execute(() -> observer.onSuccess(t));
                }
            } finally {
                dispose();
            }

        }

        @Override
        public void onError(Throwable t) {
            if (isDisposed()) {
                return;
            }

            Scheduler scheduler = this.scheduler;
            try {
                if (scheduler.inLoop()) {
                    observer.onError(t);
                } else {
                    scheduler.execute(() -> observer.onError(t));
                }
            } finally {
                dispose();
            }

        }
    }
}