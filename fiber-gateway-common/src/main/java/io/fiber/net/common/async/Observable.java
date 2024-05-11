package io.fiber.net.common.async;

import io.fiber.net.common.async.internal.Functions;
import io.fiber.net.common.async.internal.ObToMaybe;
import io.fiber.net.common.async.internal.ObservableCreate;
import io.fiber.net.common.async.internal.SchedulerNotifyObservable;

/**
 * 承诺由 subscribe 的线程发出通知，并且每个 Observable 仅可以被订阅一次。
 *
 * @param <T>
 */
public interface Observable<T> {

    interface Emitter<T> {

        void onNext(T value);

        void onError(Throwable error);

        void onComplete();

        boolean isDisposed();

    }

    interface Observer<T> {
        void onSubscribe(Disposable d);

        void onNext(T t);

        void onError(Throwable e);

        void onComplete();
    }

    @FunctionalInterface
    interface OnSubscribe<T> {

        void subscribe(Emitter<T> emitter) throws Throwable;
    }

    static <T> Observable<T> create(OnSubscribe<T> source) {
        return new ObservableCreate<>(source);
    }

    void subscribe(Observer<? super T> observer);

    default Maybe<T> toMaybe(Function2<? super T, ? super T, ? extends T> merge,
                             Consumer<? super T> disFunc) {
        return new ObToMaybe<>(this, merge, disFunc);
    }

    @SuppressWarnings("unchecked")
    default Maybe<T> toMaybe() {
        return toMaybe((Function2<? super T, ? super T, ? extends T>) Functions.getUseLaterMerger(),
                Functions.getNoopConsumer());
    }

    default Observable<T> notifyOn(Scheduler scheduler) {
        if (scheduler == Scheduler.direct()) {
            return this;
        }

        return new SchedulerNotifyObservable<>(scheduler, this);
    }

}
