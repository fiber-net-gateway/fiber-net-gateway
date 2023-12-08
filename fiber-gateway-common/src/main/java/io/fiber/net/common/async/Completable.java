package io.fiber.net.common.async;


import io.fiber.net.common.async.internal.CompletableCreate;

public interface Completable {

    interface Emitter {
        void onError(Throwable t);

        void onComplete();

        boolean isDisposed();
    }

    interface Observer {
        void onSubscribe(Disposable d);

        void onError(Throwable e);

        void onComplete();
    }

    interface OnSubscribe {

        void subscribe(Emitter emitter) throws Throwable;
    }


    static Completable create(OnSubscribe onSubscribe) {
        return new CompletableCreate(onSubscribe);
    }


    void subscribe(Observer observer);
}
