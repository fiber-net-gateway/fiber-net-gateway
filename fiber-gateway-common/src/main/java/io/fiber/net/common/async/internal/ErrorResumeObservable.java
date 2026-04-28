package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;
import io.fiber.net.common.async.Observable;

public class ErrorResumeObservable<T> implements Observable<T> {
    private final Function<? super Throwable, Observable<? extends T>> resume;
    private final Observable<T> source;

    public ErrorResumeObservable(Function<? super Throwable, Observable<? extends T>> resume, Observable<T> source) {
        this.resume = resume;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        source.subscribe(new Ob<>(resume, observer));
    }

    private static class Ob<T> extends DisposableOb implements Observer<T> {
        private final Function<? super Throwable, Observable<? extends T>> resume;
        private final Observer<? super T> downStream;
        private Disposable upstream;
        private boolean secondary;

        private Ob(Function<? super Throwable, Observable<? extends T>> resume, Observer<? super T> downStream) {
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
        public void onNext(T t) {
            downStream.onNext(t);
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
            Observable<? extends T> invoke;
            try {
                invoke = resume.invoke(e);
            } catch (Throwable ex) {
                downStream.onError(ex);
                return;
            }
            invoke.subscribe(this);
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
        }
    }
}
