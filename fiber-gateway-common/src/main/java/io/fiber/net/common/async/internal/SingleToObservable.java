package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Single;

public final class SingleToObservable<T, R> implements Observable<R> {
    private final Function<? super T, Observable<? extends R>> func;
    private final Single<T> source;

    public SingleToObservable(Function<? super T, Observable<? extends R>> func, Single<T> source) {
        this.func = func;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super R> observer) {
        source.subscribe(new Ob<>(func, observer));
    }

    private static class Ob<T, R> implements Single.Observer<T>, Observer<R>, Disposable {
        private final Function<? super T, Observable<? extends R>> func;
        private final Observer<? super R> observer;
        private Disposable dis;

        private Ob(Function<? super T, Observable<? extends R>> func, Observer<? super R> observer) {
            this.func = func;
            this.observer = observer;
        }

        @Override
        public void onSubscribe(Disposable d) {
            Disposable first = dis;
            this.dis = d;
            if (first == null) {
                observer.onSubscribe(this);
            }
        }

        @Override
        public void onNext(R r) {
            observer.onNext(r);
        }

        @Override
        public void onSuccess(T t) {
            Observable<? extends R> observable;
            try {
                observable = func.invoke(t);
            } catch (Throwable e) {
                observer.onError(e);
                return;
            }
            observable.subscribe(this);
        }

        @Override
        public void onError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void onComplete() {
            observer.onComplete();
        }

        @Override
        public boolean isDisposed() {
            return dis.isDisposed();
        }

        @Override
        public boolean dispose() {
            return dis.dispose();
        }
    }
}
