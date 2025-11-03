package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public class ObservableMap<T, R> implements Observable<R> {
    private final Observable<T> source;
    private final Function<? super T, ? extends R> mapper;

    public ObservableMap(Observable<T> source, Function<? super T, ? extends R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Observer<? super R> observer) {
        source.subscribe(new Ob<>(observer, mapper));
    }

    private static class Ob<T, R> implements Observer<T> {
        private final Observer<? super R> observer;
        private final Function<? super T, ? extends R> mapper;

        public Ob(Observer<? super R> observer, Function<? super T, ? extends R> mapper) {
            this.observer = observer;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            observer.onSubscribe(d);
        }

        @Override
        public void onNext(T t) {
            R r;
            try {
                r = mapper.invoke(t);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                observer.onError(e);
                return;
            }
            observer.onNext(r);
        }

        @Override
        public void onError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void onComplete() {
            observer.onComplete();
        }
    }
}
