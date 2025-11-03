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

    default Single<T> toSingle(T defaultValue) {
        return new MaybeToSingle<>(defaultValue, this);
    }

    default <U extends T> Single<T> flatSingle(Supplier<? extends Single<U>> supplier) {
        return new FlatSingleMaybe<>(supplier, this);
    }


    /**
     * 创建一个 Maybe. onComplete() if value is null
     *
     * @param value nullable
     * @param <T>   泛型
     * @param <V>   actual type
     * @return Maybe
     */
    static <T, V extends T> Maybe<T> just(V value) {
        return new JustMaybe<>(value, null);
    }

    static <T> Maybe<T> justErr(Throwable err) {
        return new JustMaybe<>(null, err);
    }

    static <T> Maybe<T> create(OnSubscribe<T> onSubscribe) {
        return new MaybeCreate<>(onSubscribe, Functions.getNoopConsumer());
    }

    static <T> Maybe<T> create(OnSubscribe<T> onSubscribe, Consumer<? super T> onDismiss) {
        return new MaybeCreate<>(onSubscribe, onDismiss);
    }

    default Maybe<T> notifyOn(Scheduler scheduler) {
        if (Scheduler.direct() == scheduler) {
            return this;
        }
        return new SchedulerNotifyMaybe<>(scheduler, this);
    }
}