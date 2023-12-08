package io.fiber.net.common.async;

import io.fiber.net.common.async.internal.*;

public interface Single<T> {

    interface Observer<T> {

        void onSubscribe(Disposable d);

        void onSuccess(T t);

        void onError(Throwable e);

        Scheduler scheduler();
    }

    interface Emitter<T> {

        void onSuccess(T t);

        void onError(Throwable t);

        boolean isDisposed();

    }

    interface OnSubscribe<T> {
        void subscribe(Emitter<T> emitter) throws Throwable;
    }

    static <T> Single<T> create(OnSubscribe<T> onSubscribe) {
        return new SingleCreate<>(onSubscribe);
    }

    static <T> Single<T> just(T t) {
        return new JustSingle<>(t);
    }

    void subscribe(Observer<? super T> observer);

    default Disposable subscribe(java.util.function.BiConsumer<T, Throwable> com) {
        SingleConsumeOb<T> ob = new SingleConsumeOb<>(com);
        subscribe(ob);
        return ob.getD();
    }

    default <R> Single<R> flatMap(Function<? super T, Single<? extends R>> map) {
        return new FlatMappedSingle<>(map, this);
    }

    default <R> Single<R> map(Function<? super T, ? extends R> map) {
        return new MappedSingle<>(map, this);
    }

}
