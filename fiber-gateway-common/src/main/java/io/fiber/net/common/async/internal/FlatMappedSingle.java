package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
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
        private final Ob2<U> ob2;

        private MapOb(Function<T, Single<? extends U>> map, Observer<? super U> c) {
            this.map = map;
            this.ob2 = new Ob2<>(c);
        }

        @Override
        public void onSubscribe(Disposable d) {
            ob2.onSubscribe(d);
        }


        @Override
        public void onSuccess(T t) {
            Single<? extends U> invoke;
            try {
                invoke = map.invoke(t);
            } catch (Throwable e) {
                ob2.c.onError(e);
                return;
            }

            invoke.subscribe(ob2);
        }

        @Override
        public void onError(Throwable e) {
            ob2.c.onError(e);
        }

    }

    private static class Ob2<U> extends DisposableOb implements Observer<U> {
        private final Observer<? super U> c;
        private Disposable d;

        private Ob2(Observer<? super U> c) {
            this.c = c;
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
        public void onSubscribe(Disposable d) {
            Disposable old = this.d;
            this.d = d;
            if (old == null) {
                c.onSubscribe(this);
            }
        }

        @Override
        public void onSuccess(U u) {
            c.onSuccess(u);
        }

        @Override
        public void onError(Throwable e) {
            c.onError(e);
        }
    }

}
