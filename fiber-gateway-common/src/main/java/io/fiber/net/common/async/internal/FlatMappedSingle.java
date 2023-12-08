package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;

public class FlatMappedSingle<T, U> implements Single<U> {

    private final Function<? super T, Single<? extends U>> map;
    private final Single<T> source;

    public FlatMappedSingle(Function<? super T, Single<? extends U>> fc, Single<T> source) {
        this.map = fc;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super U> observer) {
        source.subscribe(new MapOb<>(map, observer));
    }

    private static class MapOb<T, U> implements Observer<T> {
        private final Function<? super T, Single<? extends U>> map;
        private final Observer<? super U> c;

        private MapOb(Function<T, Single<? extends U>> map, Observer<? super U> c) {
            this.map = map;
            this.c = c;
        }

        @Override
        public void onSubscribe(Disposable d) {
            c.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            Single<? extends U> invoke;
            try {
                invoke = map.invoke(t);
            } catch (Throwable e) {
                c.onError(e);
                return;
            }

            invoke.subscribe(c);
        }

        @Override
        public void onError(Throwable e) {
            c.onError(e);
        }

        @Override
        public Scheduler scheduler() {
            return c.scheduler();
        }
    }


}
