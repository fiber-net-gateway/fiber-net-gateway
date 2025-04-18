package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

public class SingleToCompletable<T> implements Completable {
    private final Consumer<? super T> c;
    private final Single<T> source;

    public SingleToCompletable(Consumer<? super T> c, Single<T> source) {
        this.c = c;
        this.source = source;
    }

    @Override
    public void subscribe(Observer observer) {
        Ob<? super T> ob = new Ob<>(observer, c);
        source.subscribe(ob);
    }

    private static class Ob<T> implements Single.Observer<T> {
        private final Completable.Observer down;
        private final Consumer<? super T> c;

        private Ob(Observer down, Consumer<? super T> c) {
            this.down = down;
            this.c = c;
        }

        @Override
        public void onSubscribe(Disposable d) {
            down.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            try {
                c.accept(t);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                down.onError(e);
                return;
            }
            down.onComplete();
        }

        @Override
        public void onError(Throwable e) {
            down.onError(e);
        }
    }
}
