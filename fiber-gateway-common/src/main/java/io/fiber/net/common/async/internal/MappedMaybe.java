package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;

public class MappedMaybe<T, U> implements Maybe<U> {

    private final Function<? super T, ? extends U> map;
    private final Maybe<T> source;

    public MappedMaybe(Function<? super T, ? extends U> fc, Maybe<T> source) {
        this.map = fc;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super U> observer) {
        source.subscribe(new MapOb<>(map, observer));
    }

    private static class MapOb<T, U> implements Observer<T> {
        private final Function<? super T, ? extends U> map;
        private final Observer<? super U> c;

        private MapOb(Function<T, U> map, Observer<? super U> c) {
            this.map = map;
            this.c = c;
        }

        @Override
        public void onSubscribe(Disposable d) {
            c.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            U invoke;
            try {
                invoke = map.invoke(t);
            } catch (Throwable e) {
                c.onError(e);
                return;
            }
            c.onSuccess(invoke);
        }

        @Override
        public void onError(Throwable e) {
            c.onError(e);
        }

        @Override
        public void onComplete() {
            c.onComplete();
        }

        @Override
        public Scheduler scheduler() {
            return c.scheduler();
        }
    }
}
