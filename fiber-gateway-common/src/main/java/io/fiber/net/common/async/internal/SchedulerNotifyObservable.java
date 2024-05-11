package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;

public class SchedulerNotifyObservable<T> implements Observable<T> {

    private final Scheduler scheduler;
    private final Observable<T> source;

    public SchedulerNotifyObservable(Scheduler scheduler, Observable<T> source) {
        this.scheduler = scheduler;
        this.source = source;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        source.subscribe(new Ob<>(observer, scheduler == null ? Scheduler.current() : scheduler));
    }

    private static class Ob<T> implements Observer<T> {
        private final Observer<? super T> down;
        private final Scheduler scheduler;

        private Ob(Observer<? super T> down, Scheduler scheduler) {
            this.down = down;
            this.scheduler = scheduler;
        }

        @Override
        public void onSubscribe(Disposable d) {
            down.onSubscribe(d);
        }

        @Override
        public void onNext(T t) {
            if (scheduler.inLoop()) {
                down.onNext(t);
            } else {
                scheduler.execute(() -> down.onNext(t));
            }
        }

        @Override
        public void onError(Throwable e) {
            if (scheduler.inLoop()) {
                down.onError(e);
            } else {
                scheduler.execute(() -> down.onError(e));
            }
        }

        @Override
        public void onComplete() {
            if (scheduler.inLoop()) {
                down.onComplete();
            } else {
                scheduler.execute(down::onComplete);
            }
        }
    }
}
