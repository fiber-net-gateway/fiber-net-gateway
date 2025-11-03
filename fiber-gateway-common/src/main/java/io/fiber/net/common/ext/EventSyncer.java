package io.fiber.net.common.ext;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.*;
import io.fiber.net.common.async.internal.DisposableOb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSyncer {
    public static final FiberException VALUE_REQUIRED = new FiberException("value required", 500, "SYNC_NO_FIRST");
    private static final Logger log = LoggerFactory.getLogger(EventSyncer.class);
    private final EngineScheduler scheduler;
    private boolean synced;
    private int requireSync;

    public EventSyncer() {
        scheduler = (EngineScheduler) Scheduler.current();
    }

    private void sync() {
        if (!synced && --requireSync == 0) {
            scheduler.suspend();
            synced = true;
        }
    }

    private void requireSync() {
        if (!synced) {
            requireSync++;
        }
    }

    private void syncFailed(Throwable err) {
        if (!synced) {
            scheduler.abort(err);
            synced = true;
        }
    }

    public boolean isSynced() {
        return synced;
    }

    private class OnFirstDisposable extends DisposableOb {
        private Disposable disposable;
        protected boolean first = true;

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            if (dispose && disposable != null) {
                dispose = disposable.dispose();
                disposable = null;
                syncIfFirst();
            }
            return dispose;
        }

        protected final void syncIfFirst() {
            if (first) {
                first = false;
                sync();
            }
        }

        protected final void syncIfFirst(Throwable err) {
            if (first) {
                first = false;
                syncFailed(err);
            }
        }

        public void onSubscribe(Disposable d) {
            disposable = d;
            requireSync();
        }
    }

    private class FirstSyncerSource<T> extends OnFirstDisposable implements Observable.Observer<T> {
        private final Consumer<? super T> consumer;

        private FirstSyncerSource(Consumer<? super T> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onNext(T t) {
            try {
                consumer.accept(t);
            } catch (Throwable e) {
                onError(e);
                return;
            }
            syncIfFirst();
        }

        @Override
        public void onError(Throwable e) {
            log.error("error accept from source in once syncer", e);
            syncIfFirst(e);
        }

        @Override
        public void onComplete() {
            syncIfFirst(VALUE_REQUIRED);
        }
    }

    private class RecordFirstOb<T> extends OnFirstDisposable implements Observable.Observer<T> {
        private final Observable.Observer<? super T> downStream;

        private RecordFirstOb(Observable.Observer<? super T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void onSubscribe(Disposable d) {
            super.onSubscribe(d);
            downStream.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            downStream.onNext(t);
            syncIfFirst();
        }

        @Override
        public void onError(Throwable e) {
            downStream.onError(e);
            syncIfFirst(e);
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
            syncIfFirst(VALUE_REQUIRED);
        }
    }

    private class RecordFirstObservable<T> implements Observable<T> {
        private final Observable<T> source;

        private RecordFirstObservable(Observable<T> source) {
            this.source = source;
        }

        @Override
        public void subscribe(Observer<? super T> observer) {
            source.subscribe(new RecordFirstOb<>(observer));
        }
    }

    public <T> Disposable syncedConsume(Observable<T> source, Consumer<? super T> consumer) {
        checkScheduleLoop();
        FirstSyncerSource<T> observer = new FirstSyncerSource<>(consumer);
        source.notifyOn(scheduler)
                .subscribe(observer);
        return observer;
    }

    public <T> Observable<T> sync(Observable<T> source) {
        checkScheduleLoop();
        return new RecordFirstObservable<>(source.notifyOn(scheduler));
    }

    private void checkScheduleLoop() {
        if (!scheduler.inLoop()) {
            throw new IllegalStateException("not in scheduler loop");
        }
    }

    private class FirstSyncerSingle<T> implements Single<T> {
        private final Single<T> source;

        private FirstSyncerSingle(Single<T> source) {
            this.source = source;
        }

        @Override
        public void subscribe(Observer<? super T> observer) {
            source.subscribe(new AwaitOb<>(observer));
        }
    }

    private class AwaitOb<T> extends OnFirstDisposable implements Single.Observer<T> {
        private final Single.Observer<? super T> downStream;

        private AwaitOb(Single.Observer<? super T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void onSubscribe(Disposable d) {
            super.onSubscribe(d);
            downStream.onSubscribe(this);
        }

        @Override
        public void onSuccess(T t) {
            downStream.onSuccess(t);
            syncIfFirst();
        }

        @Override
        public void onError(Throwable e) {
            downStream.onError(e);
            syncIfFirst(e);
        }
    }


    public <T> Single<T> await(Single<T> source) {
        checkScheduleLoop();
        return new FirstSyncerSingle<>(source.notifyOn(scheduler));
    }

    public boolean trySync() {
        if (!synced && requireSync == 0) {
            scheduler.suspend();
            synced = true;
            return true;
        }
        return false;
    }

}
