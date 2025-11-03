package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public class SingleFlatCompletable<T> implements Completable {
    private final Function<? super T, ? extends Completable> map;
    private final Single<T> source;

    public SingleFlatCompletable(Function<? super T, ? extends Completable> map, Single<T> source) {
        this.map = map;
        this.source = source;
    }

    @Override
    public void subscribe(Observer observer) {
        source.subscribe(new Ob<>(map, observer));

    }

    private static class Ob<T> implements Single.Observer<T> {
        private final Function<? super T, ? extends Completable> map;
        private final Ob2 ob2;

        private Ob(Function<? super T, ? extends Completable> map, Observer observer) {
            this.map = map;
            this.ob2 = new Ob2(observer);
        }

        @Override
        public void onSubscribe(Disposable d) {
            ob2.disposable = d;
            ob2.downStream.onSubscribe(ob2);
        }

        @Override
        public void onSuccess(T t) {
            Completable c;
            try {
                c = map.invoke(t);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                onError(e);
                return;
            }
            c.subscribe(ob2);
        }

        @Override
        public void onError(Throwable e) {
            ob2.onError(e);
        }
    }

    private static class Ob2 extends DisposableOb implements Observer {
        private Disposable disposable;
        private final Observer downStream;

        private Ob2(Observer downStream) {
            this.downStream = downStream;
        }

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            Disposable d;
            if (dispose && (d = disposable) != null) {
                dispose = d.dispose();
            }
            return dispose;
        }

        @Override
        public void onError(Throwable e) {
            downStream.onError(e);
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
        }
    }
}
