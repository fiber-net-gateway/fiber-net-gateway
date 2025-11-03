package io.fiber.net.common.async;


import io.fiber.net.common.async.internal.*;

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

    default Completable onEvent(Consumer<? super Throwable> e) {
        return new OnEventCompletable(this, e);
    }

    default Completable onErrorResume(Function<? super Throwable, Completable> resume) {
        return new ErrorResumeCompletable(this, resume);
    }


    void subscribe(Observer observer);

    default Disposable subscribe(java.util.function.Consumer<Throwable> consumer) {
        FuncCompletableObserver funcCompletableObserver = new FuncCompletableObserver(consumer);
        subscribe(funcCompletableObserver);
        return funcCompletableObserver.getDisposable();
    }
}
