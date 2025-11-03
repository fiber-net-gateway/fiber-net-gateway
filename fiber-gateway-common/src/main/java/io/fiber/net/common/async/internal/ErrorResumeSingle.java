package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Single;

public class ErrorResumeSingle<T> implements Single<T> {
    private final Function<? super Throwable, Single<? extends T>> resume;
    private final Single<T> source;

    public ErrorResumeSingle(Function<? super Throwable, Single<? extends T>> resume, Single<T> source) {
        this.resume = resume;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        source.subscribe(new Ob<>(resume, observer));
    }

    private static class Ob<T> extends DisposableOb implements Observer<T> {
        private final Function<? super Throwable, Single<? extends T>> resume;
        private final Observer<? super T> downStream;
        private Disposable upstream;
        private boolean secondary;

        private Ob(Function<? super Throwable, Single<? extends T>> resume, Observer<? super T> downStream) {
            this.resume = resume;
            this.downStream = downStream;
        }

        @Override
        public void onSubscribe(Disposable d) {
            Disposable u = this.upstream;
            this.upstream = d;
            if (u == null) {
                downStream.onSubscribe(this);
            }
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            Disposable u;
            if (dispose && (u = upstream) != null) {
                dispose = u.dispose();
            }
            return dispose;
        }

        @Override
        public void onSuccess(T t) {
            downStream.onSuccess(t);
        }

        @Override
        public void onError(Throwable e) {
            if (isDisposed()) {
                return;
            }
            if (secondary) {
                downStream.onError(e);
                return;
            }

            secondary = true;
            Single<? extends T> invoke;
            try {
                invoke = resume.invoke(e);
            } catch (Throwable ex) {
                downStream.onError(ex);
                return;
            }
            invoke.subscribe(this);
        }
    }
}
