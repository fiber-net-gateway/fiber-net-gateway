package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Disposable;

public class OnEventCompletable implements Completable {
    private final Completable source;
    private final Consumer<? super Throwable> consumer;

    public OnEventCompletable(Completable source, Consumer<? super Throwable> consumer) {
        this.source = source;
        this.consumer = consumer;
    }

    @Override
    public void subscribe(Observer observer) {
        source.subscribe(new Ob(observer, consumer));
    }

    private static class Ob implements Observer {
        private final Observer downstream;
        private final Consumer<? super Throwable> consumer;

        private Ob(Observer downstream, Consumer<? super Throwable> consumer) {
            this.downstream = downstream;
            this.consumer = consumer;
        }

        @Override
        public void onSubscribe(Disposable d) {
            downstream.onSubscribe(d);
        }

        @Override
        public void onError(Throwable e) {
            try {
                consumer.accept(e);
            } catch (Throwable ex) {
                downstream.onError(ex);
                return;
            }
            downstream.onError(e);
        }

        @Override
        public void onComplete() {
            try {
                consumer.accept(null);
            } catch (Throwable e) {
                downstream.onError(e);
                return;
            }
            downstream.onComplete();
        }
    }
}
