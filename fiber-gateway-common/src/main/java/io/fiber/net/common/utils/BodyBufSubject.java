package io.fiber.net.common.utils;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.DisposableOb;
import io.fiber.net.common.async.internal.ErrorObservable;
import io.fiber.net.common.async.internal.SchedulerNotifyObservable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * limited thread safe: used for one producer and one consumer.
 */
public class BodyBufSubject implements Observable<ByteBuf> {

    protected static class ObE extends DisposableOb implements Emitter<ByteBuf> {
        private final Observer<? super ByteBuf> observer;
        private final BodyBufSubject sender;

        public ObE(Observer<? super ByteBuf> observer, BodyBufSubject sender) {
            this.observer = observer;
            this.sender = sender;
        }

        @Override
        public void onNext(ByteBuf value) {
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

    private static final AtomicReferenceFieldUpdater<BodyBufSubject, ObE> UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(BodyBufSubject.class, ObE.class, "obE");

    private static final ObE DISMISS = new ObE(null, null);
    private static final ObE STARTED = new ObE(null, null);
    private static final ObE COMPLETED = new ObE(null, null);


    private final Scheduler scheduler;
    private int received;
    private BodyBufSubject current = this;
    private ByteBuf input;
    private Throwable err;
    private boolean completed;
    private volatile ObE obE;


    public BodyBufSubject() {
        this(Scheduler.current());
    }

    public BodyBufSubject(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    private static boolean isSubscribed(ObE o) {
        return o != null && o != STARTED && o != COMPLETED;
    }

    private boolean isSubscribed0() {
        return isSubscribed(obE);
    }

    protected ByteBuf noSubscriberMerge(ByteBuf previous, ByteBuf current) {
        if (previous instanceof CompositeByteBuf) {
            ((CompositeByteBuf) previous).addFlattenedComponents(true, current);
            return previous;
        }
        CompositeByteBuf byteBufs = ByteBufAllocator.DEFAULT.compositeBuffer();
        byteBufs.addFlattenedComponents(true, previous);
        byteBufs.addFlattenedComponents(true, current);
        return byteBufs;
    }

    protected void onDismissClear(ByteBuf value) {
        value.release();
    }

    @Override
    public Maybe<ByteBuf> toMaybe() {
        return toMaybe(this::noSubscriberMerge, this::onDismissClear);
    }

    public Scheduler getProducerScheduler() {
        return scheduler;
    }

    public void onNext(ByteBuf value) {
        int i = value.touch().readableBytes();
        if (i == 0) {
            value.release();
            return;
        }
        received += i;

        ObE e = UPDATER.get(this);
        if (e == null) {
            input = value;
            if (UPDATER.compareAndSet(this, null, STARTED)) {
                return;
            }

            e = UPDATER.get(this);
            // producer would not set state concurrently;
            assert e != null && e != STARTED && e != COMPLETED;
            input = null;
        }

        assert e != COMPLETED;

        if (e == DISMISS) {
            onDismissClear(value);
            return;
        }

        ByteBuf input = this.input;
        if (e == STARTED) {
            if (input != null) {
                this.input = noSubscriberMerge(input, value);
            } else {
                this.input = value;
            }
            return;
        }

        if (input != null) { // maybe have prev value... if subscribe after start and postInput not invoked
            this.input = null;
            e.onNext(input);
        }

        e.onNext(value);

    }

    public void onError(Throwable error) {
        completed = true;
        ObE e = UPDATER.get(this);
        if (e == null || e == STARTED) {
            err = error;
            if (UPDATER.compareAndSet(this, e, COMPLETED)) {
                return;
            }
            e = UPDATER.get(this);
            // producer would not set state concurrently;
            assert e != null && e != STARTED;
            err = null;
        }

        assert e != COMPLETED;
        if (e == DISMISS) {
            // maybe input has value. clearInput will destroy it
            return;
        }
        ByteBuf t = input;
        if (t != null) {
            this.input = null;
            e.onNext(t);
        }
        e.onError(error);
    }

    public void onComplete() {
        completed = true;
        ObE e = UPDATER.get(this);
        if (e == null || e == STARTED) {
            if (UPDATER.compareAndSet(this, e, COMPLETED)) {
                return;
            }
            e = UPDATER.get(this);
            assert e != null && e != STARTED;
        }

        assert e != COMPLETED;
        if (e == DISMISS) {
            // maybe input has value. clearInput will destroy it
            return;
        }

        ByteBuf t = this.input;
        if (t != null) {
            this.input = null;
            e.onNext(t);
        }
        e.onComplete();
    }

    private void obSubscribe(Observer<? super ByteBuf> observer) {
        ObE e = new ObE(observer, this);
        observer.onSubscribe(e);
        ObE o;
        do {
            if (isSubscribed(o = UPDATER.get(this))) {
                e.onError(Exceptions.OB_CONSUMED);
                return;
            }
        } while (!UPDATER.compareAndSet(this, o, e));

        if (o == COMPLETED) {
            ByteBuf input = this.input;
            Throwable err = this.err;
            this.input = null;
            this.err = null;
            if (input != null) {
                e.onNext(input);
            }
            if (err != null) {
                e.onError(err);
            } else {
                e.onComplete();
            }

        } else if (o == STARTED) {
            // before complete. input and err only can accessed by producer.
            // if producer already notify something. the observer not received event until producer notify.
            // so we need notify as the producer
            if (scheduler.inLoop()) {
                postInput(e);
            } else {
                scheduler.execute(() -> postInput(e));
            }
        }
    }

    private void postInput(ObE e) {
        ByteBuf t;
        if ((t = input) != null) {
            input = null;
            e.onNext(t);
        }
    }

    @Override
    public Observable<ByteBuf> notifyOn(Scheduler scheduler) {
        if (scheduler == this.scheduler || scheduler == Scheduler.direct()) {
            return this;
        }
        return new SchedulerNotifyObservable<>(scheduler, this);
    }

    public int getReceived() {
        return received;
    }

    public Observable<ByteBuf> fork() {
        if (current == null) {
            return ErrorObservable.getConsumed();
        }
        Forked forked = new Forked(scheduler);
        BodyBufSubject c;
        if ((c = current) == null) {
            return ErrorObservable.getConsumed();
        } else {
            current = forked;
        }

        c.obSubscribe(forked);
        return forked.dumped;
    }


    public boolean isSubscribed() {
        BodyBufSubject c = current;
        if (c != null) {
            return c.isSubscribed0();
        }
        return true;
    }

    public boolean isCompleted() {
        return completed;
    }


    @Override
    public void subscribe(Observer<? super ByteBuf> observer) {
        assert !(this instanceof Forked);
        BodyBufSubject c;
        if ((c = current) == null) {
            ErrorObservable.<ByteBuf>getConsumed().subscribe(observer);
            return;
        } else {
            current = null;
        }
        c.obSubscribe(observer);
    }

    private void clearInput() {
        assert scheduler.inLoop();
        err = null;
        ByteBuf i;
        if ((i = input) != null) {
            input = null;
            onDismissClear(i);
        }
    }

    private void obDismiss() {
        ObE e;
        for (; ; ) {
            e = UPDATER.get(this);
            if (e == null || e == STARTED || e == COMPLETED) {
                if (UPDATER.compareAndSet(this, e, DISMISS)) {
                    break;
                }
            } else {
                return;
            }
        }

        if (e == COMPLETED) {
            clearInput();
        } else if (e == STARTED) {
            if (scheduler.inLoop()) {
                clearInput();
            } else {
                scheduler.execute(this::clearInput);
            }
        }
    }

    public void dismiss() {
        BodyBufSubject c;
        if ((c = current) == null) {
            return;
        }
        c.obDismiss();
    }

    private static class Forked extends BodyBufSubject implements Observer<ByteBuf> {
        private final BodyBufSubject dumped;

        private Forked(Scheduler scheduler) {
            super(scheduler);
            this.dumped = new BodyBufSubject(scheduler);
        }

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(ByteBuf value) {
            dumped.onNext(value.retainedSlice());
            super.onNext(value);
        }

        @Override
        public void onError(Throwable error) {
            dumped.onError(error);
            super.onError(error);
        }

        @Override
        public void onComplete() {
            dumped.onComplete();
            super.onComplete();
        }
    }

}