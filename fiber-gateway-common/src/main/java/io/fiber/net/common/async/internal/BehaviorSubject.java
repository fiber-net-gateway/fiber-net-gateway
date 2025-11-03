package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Observable;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BehaviorSubject<T> implements Observable<T> {
    private static final Object TERMINATED = new Object();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicReference<Object> value = new AtomicReference<>();
    private final ArrayList<Ob<T>> observers = new ArrayList<>();
    private boolean error;

    public void onNext(T value) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        this.value.set(value);
        try {
            for (Ob<T> ob : observers) {
                if (!ob.isDisposed()) {
                    ob.observer.onNext(value);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    public boolean hasValue() {
        return value.get() != null;
    }

    public void onError(Throwable err) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        error = true;
        this.value.set(err);
        try {
            for (Ob<T> ob : observers) {
                if (!ob.isDisposed()) {
                    ob.observer.onError(err);
                }
            }
            observers.clear();
        } finally {
            readLock.unlock();
        }
    }

    public void onComplete() {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        this.value.set(TERMINATED);
        try {
            for (Ob<T> ob : observers) {
                if (!ob.isDisposed()) {
                    ob.observer.onComplete();
                }
            }
            observers.clear();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void subscribe(Observer<? super T> observer) {
        Ob<T> ob = new Ob<>(this, observer);
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            observers.add(ob);
            observer.onSubscribe(ob);
            if (!ob.isDisposed()) {
                Object object = value.get();
                if (error) {
                    observers.remove(ob);
                    observer.onError((Throwable) object);
                } else if (object == TERMINATED) {
                    observers.remove(ob);
                    observer.onComplete();
                } else if (object != null) {
                    observer.onNext((T) object);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static class Ob<T> extends DisposableOb {
        private final BehaviorSubject<T> parent;
        private final Observer<? super T> observer;

        private Ob(BehaviorSubject<T> parent, Observer<? super T> observer) {
            this.parent = parent;
            this.observer = observer;
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            if (dispose) {
                remove();
            }
            return dispose;
        }


        private void remove() {
            ReentrantReadWriteLock.WriteLock writeLock = parent.lock.writeLock();
            writeLock.lock();
            try {
                parent.observers.remove(this);
            } finally {
                writeLock.unlock();
            }
        }

    }
}
