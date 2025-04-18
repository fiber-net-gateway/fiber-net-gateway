package io.fiber.net.common.async;

import io.fiber.net.common.async.internal.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface Single<T> {

    interface Observer<T> {

        void onSubscribe(Disposable d);

        void onSuccess(T t);

        void onError(Throwable e);
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

    default Single<T> notifyOn(Scheduler scheduler) {
        if (Scheduler.direct() == scheduler) {
            return this;
        }
        return new SchedulerNotifySingle<>(scheduler, this);
    }

    default Single<T> mapError(Function<Throwable, Throwable> mapErr) {
        return new ErrMappedSingle<>(mapErr, this);
    }

    default <R> Observable<R> switchToObservable(Function<? super T, Observable<? extends R>> function) {
        return new SingleToObservable<T, R>(function, this);
    }

    @SuppressWarnings("unchecked")
    static <T, R> Single<R> zip(Iterable<? extends Single<? extends T>> sources,
                                Function<? super Object[], ? extends R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        ArrayList<Single<? extends T>> sourceList = new ArrayList<>();
        for (Single<? extends T> source : sources) {
            sourceList.add(source);
        }
        return new SingleZipIterable<>(sourceList.toArray((Single<? extends T>[]) new Single[sourceList.size()]), zipper);
    }

    @SuppressWarnings("unchecked")
    static <T, R> Single<R> zip(List<? extends Single<? extends T>> sourceList,
                                Function<? super Object[], ? extends R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sourceList, "sources is null");
        return new SingleZipIterable<>(sourceList.toArray((Single<? extends T>[]) new Single[sourceList.size()]), zipper);
    }

    default Completable toCompletable() {
        return toCompletable(Functions.getNoopConsumer());
    }

    default Completable toCompletable(Consumer<? super T> con) {
        return new SingleToCompletable<>(con, this);
    }
}
