package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.*;

public class ObToMaybe<T> implements Maybe<T> {
    private final Observable<T> parent;
    private final Function2<? super T, ? super T, ? extends T> merge;
    private final Consumer<? super T> disFunc;

    public ObToMaybe(Observable<T> parent, Function2<? super T, ? super T, ? extends T> merge,
                     Consumer<? super T> disFunc) {
        this.parent = parent;
        this.merge = merge;
        this.disFunc = disFunc;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        parent.subscribe(new ObAdaptor<>(observer, merge, disFunc));
    }

    private static class ObAdaptor<T> implements Observable.Observer<T> {
        private final Observer<? super T> down;
        private final Function2<? super T, ? super T, ? extends T> merge;
        private final Consumer<? super T> disFunc;

        private Disposable disposable;
        private T value;

        private ObAdaptor(Observer<? super T> down,
                          Function2<? super T, ? super T, ? extends T> merge,
                          Consumer<? super T> disFunc) {
            this.down = down;
            this.merge = merge;
            this.disFunc = disFunc;
        }

        @Override
        public void onSubscribe(Disposable d) {
            down.onSubscribe(disposable = d);
        }

        @Override
        public void onNext(T t) {
            if (value != null) {
                try {
                    value = merge.invoke(value, t);
                } catch (Throwable e) {
                    try {
                        disFunc.accept(value);
                    } catch (Throwable ignore) {
                    }
                    try {
                        disFunc.accept(t);
                    } catch (Throwable ignore) {
                    }
                    value = null;
                    onError(e);
                    disposable.dispose();
                }
            } else {
                value = t;
            }
        }

        @Override
        public void onError(Throwable e) {
            if (value != null) {
                try {
                    disFunc.accept(value);
                } catch (Throwable ignore) {
                }
                value = null;
            }

            down.onError(e);
        }

        @Override
        public void onComplete() {
            if (value != null) {
                down.onSuccess(value);
            } else {
                down.onComplete();
            }
        }

        @Override
        public Scheduler scheduler() {
            return down.scheduler();
        }
    }
}
