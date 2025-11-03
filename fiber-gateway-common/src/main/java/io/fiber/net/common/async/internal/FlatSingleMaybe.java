package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.async.Supplier;
import io.fiber.net.common.utils.Exceptions;

public class FlatSingleMaybe<T, U extends T> implements Single<T> {

    private final Supplier<? extends Single<U>> fc;
    private final Maybe<T> source;

    public FlatSingleMaybe(Supplier<? extends Single<U>> fc, Maybe<T> source) {
        this.fc = fc;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        source.subscribe(new MapOb<>(fc, observer));
    }

    private static class MapOb<T, U extends T> implements Maybe.Observer<T> {
        private final Supplier<? extends Single<U>> map;
        private final Ob2<T> ob2;

        private MapOb(Supplier<? extends Single<U>> map, Observer<? super T> c) {
            this.map = map;
            this.ob2 = new Ob2<>(c);
        }

        @Override
        public void onSubscribe(Disposable d) {
            ob2.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            ob2.onSuccess(t);
        }


        @Override
        public void onError(Throwable e) {
            ob2.onError(e);
        }

        @Override
        public void onComplete() {
            Single<U> single;
            try {
                single = map.get();
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                ob2.onError(e);
                return;
            }

            single.subscribe(ob2);
        }

    }

    private static class Ob2<T> extends DisposableOb implements Observer<T> {
        private final Observer<? super T> c;
        private Disposable d;

        private Ob2(Observer<? super T> c) {
            this.c = c;
        }

        @Override
        public void onSubscribe(Disposable d) {
            Disposable old = this.d;
            this.d = d;
            if (old == null) {
                c.onSubscribe(this);
            }
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            Disposable d;
            if (dispose && (d = this.d) != null) {
                dispose = d.dispose();
            }
            return dispose;
        }


        @Override
        public void onSuccess(T u) {
            c.onSuccess(u);
        }

        @Override
        public void onError(Throwable e) {
            c.onError(e);
        }
    }

}
