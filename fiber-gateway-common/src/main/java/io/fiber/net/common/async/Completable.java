package io.fiber.net.common.async;


import io.fiber.net.common.async.internal.CompletableCreate;
import io.fiber.net.common.async.internal.FuncCompletableObserver;
import io.fiber.net.common.async.internal.FuncMaybeObserver;
import io.fiber.net.common.async.internal.JustCompletable;

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

    static Completable success() {
        return JustCompletable.ofSec();
    }

    static Completable error(Throwable err) {
        return JustCompletable.ofErr(err);
    }

    void subscribe(Observer observer);

    default Disposable subscribe(java.util.function.Consumer<Throwable> consumer) {
        FuncCompletableObserver funcCompletableObserver = new FuncCompletableObserver(consumer);
        subscribe(funcCompletableObserver);
        return funcCompletableObserver.getDisposable();
    }
}
