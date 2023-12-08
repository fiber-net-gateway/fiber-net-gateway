package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.BiConsumer;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;

public class OnNotifyWrapMaybe<T> implements Maybe<T> {
    final BiConsumer<? super T, Throwable> onNotify;
    final Maybe<T> source;

    public OnNotifyWrapMaybe(BiConsumer<? super T, Throwable> onNotify, Maybe<T> source) {
        this.onNotify = onNotify;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        source.subscribe(new Ob<>(onNotify, observer));
    }

    private static class Ob<T> implements Observer<T> {
        final BiConsumer<? super T, Throwable> onNotify;
        final Observer<? super T> observer;

        private Ob(BiConsumer<? super T, Throwable> onNotify, Observer<? super T> observer) {
            this.onNotify = onNotify;
            this.observer = observer;
        }

        @Override
        public void onSubscribe(Disposable d) {
            observer.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            try {
                onNotify.accept(t, null);
                observer.onSuccess(t);
            } catch (Throwable e) {
                observer.onError(e);
            }
        }

        @Override
        public void onError(Throwable e) {
            try {
                onNotify.accept(null, e);
            } catch (Throwable ex) {
                e = ex;
            }
            observer.onError(e);
        }

        @Override
        public void onComplete() {
            try {
                onNotify.accept(null, null);
            } catch (Throwable e) {
                observer.onError(e);
                return;
            }
            observer.onComplete();
        }

        @Override
        public Scheduler scheduler() {
            return observer.scheduler();
        }
    }
}
