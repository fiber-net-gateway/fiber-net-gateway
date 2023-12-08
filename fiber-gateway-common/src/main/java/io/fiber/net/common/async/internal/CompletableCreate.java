package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.utils.Exceptions;

public final class CompletableCreate implements Completable {

    final OnSubscribe source;

    public CompletableCreate(OnSubscribe source) {
        this.source = source;
    }

    @Override
    public void subscribe(Observer observer) {
        ObE obE = new ObE(observer);
        observer.onSubscribe(obE);
        try {
            source.subscribe(obE);
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            obE.onError(e);
        }
    }

    static class ObE extends DisposableOb implements Emitter {
        private final Observer observer;

        ObE(Observer observer) {
            this.observer = observer;
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