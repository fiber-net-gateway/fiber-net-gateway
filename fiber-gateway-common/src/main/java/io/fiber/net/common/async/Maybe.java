package io.fiber.net.common.async;

import io.fiber.net.common.async.internal.*;

public interface Maybe<T> {

    interface Emitter<T> {
        void onSuccess(T t);

        void onError(Throwable t);

        void onComplete();

        boolean isDisposed();
    }

    interface Observer<T> {
        void onSubscribe(Disposable d);

        void onSuccess(T t);

        void onError(Throwable e);

        void onComplete();

        Scheduler scheduler();
    }

    interface OnSubscribe<T> {

        void subscribe(Emitter<T> emitter) throws Throwable;
    }

    void subscribe(Observer<? super T> observer);

    default Maybe<T> onNotify(BiConsumer<? super T, Throwable> onNotify) {
        return new OnNotifyWrapMaybe<>(onNotify, this);
    }

    default <U> Maybe<U> map(Function<? super T, ? extends U> function) {
        return new MappedMaybe<>(function, this);
    }

    default Disposable subscribe(java.util.function.BiConsumer<T, Throwable> consumer) {
        FuncMaybeObserver<T> tFuncMaybeObserver = new FuncMaybeObserver<>(consumer);
        subscribe(tFuncMaybeObserver);
        return tFuncMaybeObserver.getDisposable();
    }


    static <T> Maybe<T> create(OnSubscribe<T> onSubscribe) {
        return new MaybeCreate<>(onSubscribe);
    }

    @SuppressWarnings("unchecked")
    static <T> Maybe<T> ofErr(Throwable err) {
        return (Maybe<T>) new ErrMaybe(err);
    }
}