package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.Exceptions;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;


@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class SingleSubject<T> implements Single<T> {

    private static final AtomicReferenceFieldUpdater<SingleSubject, Ob> UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(SingleSubject.class, Ob.class, "ob");
    protected static final Ob DISMISS = new Ob<>(null, null);
    private static final Ob COMPLETED = new Ob<>(null, null);


    private volatile Ob<T> ob;

    private T value;
    private Throwable error;

    public final boolean dismiss() {
        Ob<T> o;
        for (; ; ) {
            o = UPDATER.get(this);
            if (o == null || o == COMPLETED) {
                if (UPDATER.compareAndSet(this, o, DISMISS)) {
                    break;
                }
            } else {
                return false;
            }
        }

        if (o == COMPLETED) {
            if (error != null) {
                error = null;
            } else {
                onDismissClear(value);
                value = null;
            }
        }
        return true;
    }

    public boolean isSubscribed() {
        Ob<T> o = ob;
        return o != null && o != COMPLETED;
    }


    @Override
    public void subscribe(Observer<? super T> observer) {
        Ob<T> e = new Ob<>(observer, this);
        observer.onSubscribe(e);

        Ob<T> o;
        for (; ; ) {
            o = UPDATER.get(this);
            if (o == null || o == COMPLETED) {
                if (UPDATER.compareAndSet(this, o, e)) {
                    break;
                }
            } else {
                e.onError0(Exceptions.OB_CONSUMED);
                return;
            }
        }

        if (o == COMPLETED) {
            if (error != null) {
                e.onError0(error);
                error = null;
            } else {
                e.onSuccess0(value);
                value = null;
            }
        }
    }

    protected abstract void onDismissClear(T value);


    public void onSuccess(T t) {
        for (; ; ) {
            Ob<T> o = UPDATER.get(this);
            assert o != COMPLETED;
            if (o == null) {
                value = t;
                if (UPDATER.compareAndSet(this, null, COMPLETED)) {
                    return;
                }
                value = null;
            } else {
                o.onSuccess(t);
                return;
            }
        }
    }

    public void onError(Throwable e) {
        for (; ; ) {
            Ob<T> o = UPDATER.get(this);
            assert o != COMPLETED;
            if (o == null) {
                error = e;
                if (UPDATER.compareAndSet(this, null, COMPLETED)) {
                    return;
                }
                error = null;
            } else {
                o.onError(e);
                return;
            }
        }
    }

    protected static class Ob<T> extends DisposableOb implements Emitter<T> {
        final Observer<? super T> observer;
        final SingleSubject<T> source;

        private Ob(Observer<? super T> observer, SingleSubject<T> source) {
            this.observer = observer;
            this.source = source;
        }

        @Override
        public void onError(Throwable error) {
            if (isDisposed()) {
                return;
            }
            Scheduler scheduler = observer.scheduler();
            if (scheduler.inLoop()) {
                observer.onError(error);
            } else {
                scheduler.execute(() -> onError0(error));
            }
        }

        public void onError0(Throwable error) {
            assert observer.scheduler().inLoop();
            if (isDisposed()) {
                return;
            }
            observer.onError(error);
        }

        @Override
        public void onSuccess(T value) {
            if (isDisposed()) {
                source.onDismissClear(value);
                return;
            }
            Scheduler scheduler = observer.scheduler();
            if (scheduler.inLoop()) {
                observer.onSuccess(value);
            } else {
                scheduler.execute(() -> onSuccess0(value));
            }
        }

        public void onSuccess0(T value) {
            assert observer.scheduler().inLoop();
            if (isDisposed()) {
                source.onDismissClear(value);
                return;
            }
            observer.onSuccess(value);
        }
    }
}
