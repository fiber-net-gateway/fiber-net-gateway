package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;


@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class Subject<T> implements Observable<T> {

    private static final AtomicReferenceFieldUpdater<Subject, ObE> UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(Subject.class, ObE.class, "obE");
    protected static final ObE DISMISS = new ObE<>(null, null);
    private static final ObE STARTED = new ObE<>(null, null);
    private static final ObE COMPLETED = new ObE<>(null, null);

    private volatile ObE<T> obE;
    private T previous;
    private Throwable err;
    private boolean subscriberClear;

    @Override
    public void subscribe(Observer<? super T> observer) {
        ObE<T> e = new ObE<>(observer, this);
        observer.onSubscribe(e);
        ObE<T> o;
        do {
            if (isSubscribed(o = UPDATER.get(this))) {
                e.onError(Exceptions.OB_CONSUMED);
                return;
            }
        } while (!UPDATER.compareAndSet(this, o, e));

        if (o == COMPLETED || o == STARTED) {
            assert !subscriberClear;
            Throwable error;
            T t;
            synchronized (this) {
                subscriberClear = true;
                t = previous;
                error = err;
                previous = null;
            }
            assert t != null || error != null;

            if (t != null) {
                e.onNext(t);
            }

            if (o != COMPLETED) {
                return;
            }
            if (error != null) {
                e.onError(error);
            } else {
                e.onComplete();
            }
        }
    }

    public boolean dismiss() {
        ObE e;
        for (; ; ) {
            e = UPDATER.get(this);
            if (e == null || e == STARTED || e == COMPLETED) {
                if (UPDATER.compareAndSet(this, e, DISMISS)) {
                    break;
                }
            } else {
                return false;
            }
        }

        if (e == STARTED || e == COMPLETED) {
            T t;
            assert !subscriberClear;
            synchronized (this) {
                subscriberClear = true;
                t = previous;
                previous = null;
                err = null;
            }

            if (t != null) {
                onDismissClear(t);
            }
        }

        return true;
    }

    private static <T> boolean isSubscribed(ObE<T> o) {
        return o != null && o != STARTED && o != COMPLETED;
    }


    public boolean isSubscribed() {
        return isSubscribed(obE);
    }

    public void onNext(T value) {
        for (; ; ) {
            ObE e = UPDATER.get(this);
            if (e == null) {
                previous = value;
                if (UPDATER.compareAndSet(this, null, STARTED)) {
                    return;
                }

                e = UPDATER.get(this);
                assert e != null && e != STARTED && e != COMPLETED;
                value = previous;
                previous = null;
            }

            assert e != COMPLETED;

            if (e == DISMISS) {
                onDismissClear(value);
                return;
            }

            if (e == STARTED) {
                synchronized (this) {
                    if (subscriberClear) {
                        continue;
                    } else {
                        assert previous != null;
                        previous = noSubscriberMerge(previous, value);
                        return;
                    }
                }
            }

            e.onNext(value);
            return;
        }
    }

    protected abstract T noSubscriberMerge(T previous, T current);

    protected abstract void onDismissClear(T value);

    public void onError(Throwable error) {
        for (; ; ) {
            ObE e = UPDATER.get(this);
            if (e == null) {
                err = error;
                if (UPDATER.compareAndSet(this, null, COMPLETED)) {
                    return;
                }

                e = UPDATER.get(this);
                assert e != null && e != STARTED && e != COMPLETED;
                err = null;
            }

            assert e != COMPLETED;
            if (e == DISMISS) {
                return;
            }

            if (e == STARTED) {
                synchronized (this) {
                    if (subscriberClear) {
                        continue;
                    } else {
                        err = error;
                    }
                }
                if (UPDATER.compareAndSet(this, STARTED, COMPLETED)) {
                    return;
                } else {
                    continue;
                }
            }
            e.onError(error);
            return;
        }


    }

    public void onComplete() {
        for (; ; ) {
            ObE e = UPDATER.get(this);
            if (e == null) {
                if (UPDATER.compareAndSet(this, null, COMPLETED)) {
                    return;
                }

                e = UPDATER.get(this);
                assert e != null && e != STARTED && e != COMPLETED;
            }

            assert e != COMPLETED;
            if (e == DISMISS) {
                return;
            }

            if (e == STARTED) {
                synchronized (this) {
                    if (subscriberClear) {
                        continue;
                    }
                }
                if (UPDATER.compareAndSet(this, STARTED, COMPLETED)) {
                    return;
                } else {
                    continue;
                }
            }
            e.onComplete();
            return;
        }
    }


    protected static class ObE<T> extends DisposableOb implements Emitter<T> {
        private final Observer<? super T> observer;
        private final Subject<T> sender;

        public ObE(Observer<? super T> observer, Subject<T> sender) {
            this.observer = observer;
            this.sender = sender;
        }

        @Override
        public void onNext(T value) {
            if (isDisposed()) {
                sender.onDismissClear(value);
                return;
            }
            observer.onNext(value);
        }

        @Override
        public void onError(Throwable error) {
            if (isDisposed()) {
                return;
            }
            observer.onError(error);
        }

        @Override
        public void onComplete() {
            if (isDisposed()) {
                return;
            }
            observer.onComplete();
        }
    }
}
