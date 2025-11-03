package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

public class ObservableToCompletable<T> implements Completable {
    private final Observable<T> source;
    private final Consumer<? super T> consumer;

    public ObservableToCompletable(Observable<T> source, Consumer<? super T> consumer) {
        this.source = source;
        this.consumer = consumer;
    }

    @Override
    public void subscribe(Observer observer) {
        source.subscribe(new Ob<>(observer, consumer));
    }

    private static class Ob<T> extends DisposableOb implements Observable.Observer<T> {
        private final Observer downStream;
        private final Consumer<? super T> consumer;
        private Disposable disposable;

        private Ob(Observer downStream, Consumer<? super T> consumer) {
            this.downStream = downStream;
            this.consumer = consumer;
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            if (dispose) {
                disposable.dispose();
            }
            return dispose;
        }

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
            downStream.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            try {
                consumer.accept(t);
            } catch (Throwable e) {
                disposable.dispose();
                Exceptions.throwIfFatal(e);
                downStream.onError(e);
            }
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
