package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;

public class ObFirstToMaybe<T> implements Maybe<T> {
    private final Observable<T> parent;

    public ObFirstToMaybe(Observable<T> parent) {
        this.parent = parent;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObAdaptor<? super T> obAdaptor = new ObAdaptor<>(observer);
        parent.subscribe(obAdaptor);
    }

    private static class ObAdaptor<T> implements Observable.Observer<T> {
        private final Observer<? super T> observer;
        private Disposable d;
        private boolean done;

        private ObAdaptor(Observer<? super T> observer) {
            this.observer = observer;
        }

        @Override
        public void onSubscribe(Disposable d) {
            observer.onSubscribe(d);
            this.d = d;
        }

        @Override
        public void onNext(T t) {
            d.dispose();
            if (done) {
                return;
            }
            done = true;
            observer.onSuccess(t);
        }

        @Override
        public void onError(Throwable e) {
            done = true;
            observer.onError(e);
        }

        @Override
        public void onComplete() {
            done = true;
            observer.onComplete();
        }
    }
}
