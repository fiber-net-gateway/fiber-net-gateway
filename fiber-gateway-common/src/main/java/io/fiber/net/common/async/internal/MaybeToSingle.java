package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Single;

public class MaybeToSingle<T> implements Single<T> {
    private final T defaultValue;
    private final Maybe<T> tMaybe;

    public MaybeToSingle(T defaultValue, Maybe<T> tMaybe) {
        this.defaultValue = defaultValue;
        this.tMaybe = tMaybe;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        tMaybe.subscribe(new ObE<>(observer, defaultValue));
    }

    private static class ObE<T> implements Maybe.Observer<T> {
        private final Single.Observer<? super T> observer;
        private final T defaultValue;

        private ObE(Observer<? super T> observer, T defaultValue) {
            this.observer = observer;
            this.defaultValue = defaultValue;
        }

        @Override
        public void onSubscribe(Disposable d) {
            observer.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            observer.onSuccess(t);
        }

        @Override
        public void onError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void onComplete() {
            observer.onSuccess(defaultValue);
        }
    }
}
