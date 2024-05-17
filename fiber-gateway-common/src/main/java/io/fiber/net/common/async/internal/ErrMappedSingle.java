package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public class ErrMappedSingle<U> implements Single<U> {

    private final Function<Throwable, Throwable> map;
    private final Single<U> source;

    public ErrMappedSingle(Function<Throwable, Throwable> map, Single<U> source) {
        this.map = map;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super U> observer) {
        source.subscribe(new MapOb<>(map, observer));
    }

    private static class MapOb<U> implements Observer<U> {
        private final Function<Throwable, Throwable> map;
        private final Observer<? super U> c;

        private MapOb(Function<Throwable, Throwable> map, Observer<? super U> c) {
            this.map = map;
            this.c = c;
        }

        @Override
        public void onSubscribe(Disposable d) {
            c.onSubscribe(d);
        }

        @Override
        public void onSuccess(U u) {
            c.onSuccess(u);
        }


        @Override
        public void onError(Throwable err) {
            try {
                err = map.invoke(err);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                c.onError(e);
                return;
            }
            c.onError(err);
        }
    }
}
