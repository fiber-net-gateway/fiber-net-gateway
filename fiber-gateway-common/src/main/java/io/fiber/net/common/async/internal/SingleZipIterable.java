package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

import java.util.concurrent.atomic.AtomicInteger;


public final class SingleZipIterable<T, R> implements Single<R> {

    final Single<? extends T>[] sources;

    final Function<? super Object[], ? extends R> zipper;

    public SingleZipIterable(Single<? extends T>[] sources, Function<? super Object[], ? extends R> zipper) {
        this.sources = sources;
        this.zipper = zipper;
    }

    @Override
    public void subscribe(Observer<? super R> observer) {
        int n = sources.length;
        if (n == 1) {
            sources[0].subscribe(new SingletonArrayOb<>(observer, zipper));
            return;
        }

        ZipCoordinator<T, R> parent = new ZipCoordinator<>(zipper, n, observer);

        observer.onSubscribe(parent);

        for (int i = 0; i < n; i++) {
            if (parent.isDisposed()) {
                return;
            }

            sources[i].subscribe(parent.obs[i]);
        }
    }

    static final class SingletonArrayOb<R, T> implements Observer<T> {
        private final Observer<R> observer;
        private final Function<? super Object[], ? extends R> zipper;

        SingletonArrayOb(Observer<R> observer, Function<? super Object[], ? extends R> zipper) {
            this.observer = observer;
            this.zipper = zipper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            observer.onSubscribe(d);
        }

        @Override
        public void onSuccess(T t) {
            R r;
            try {
                r = zipper.invoke(new Object[]{t});
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                observer.onError(e);
                return;
            }

            observer.onSuccess(r);
        }

        @Override
        public void onError(Throwable e) {
            observer.onError(e);
        }
    }

    private static class ZipCoordinator<T, R> extends AtomicInteger implements Disposable {
        final Function<? super Object[], ? extends R> zipper;
        final Ob<T>[] obs;
        final Object[] arr;
        final Observer<? super R> observer;

        @SuppressWarnings("unchecked")
        private ZipCoordinator(Function<? super Object[], ? extends R> zipper, int n, Observer<? super R> observer) {
            this.zipper = zipper;
            this.obs = new Ob[n];
            arr = new Object[n];
            this.observer = observer;
            for (int i = 0; i < n; i++) {
                obs[i] = new Ob<>(i, this);
            }
        }

        @Override
        public boolean isDisposed() {
            return get() <= 0;
        }

        @Override
        public boolean dispose() {
            if (getAndSet(0) > 0) {
                for (Ob<?> d : obs) {
                    d.dispose();
                }
                return true;
            }
            return false;
        }

        private void notify(int idx, T t) {
            arr[idx] = t;
            if (decrementAndGet() == 0) {
                R r;
                try {
                    r = zipper.invoke(arr);
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    observer.onError(e);
                    return;
                }
                observer.onSuccess(r);
            }
        }

        void disposeExcept(int index) {
            Ob<T>[] observers = this.obs;
            int n = observers.length;
            for (int i = 0; i < index; i++) {
                observers[i].dispose();
            }
            for (int i = index + 1; i < n; i++) {
                observers[i].dispose();
            }
        }

        void innerError(Throwable ex, int index) {
            if (getAndSet(0) > 0) {
                disposeExcept(index);
                observer.onError(ex);
            }
        }
    }

    private static class Ob<T> implements Observer<T> {
        private final int idx;
        private final ZipCoordinator<T, ?> num;
        private Disposable d;

        private Ob(int idx, ZipCoordinator<T, ?> num) {
            this.idx = idx;
            this.num = num;
        }

        void dispose() {
            if (d != null) {
                d.dispose();
            }
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.d = d;
        }

        @Override
        public void onSuccess(T t) {
            if (num.get() > 0) {
                num.notify(idx, t);
            }
        }

        @Override
        public void onError(Throwable e) {
            num.innerError(e, idx);
        }
    }
}
