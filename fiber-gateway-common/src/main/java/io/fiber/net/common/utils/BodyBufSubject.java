package io.fiber.net.common.utils;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.ConsumedObservable;
import io.fiber.net.common.async.internal.Subject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class BodyBufSubject extends Subject<ByteBuf> {
    private static final AtomicReferenceFieldUpdater<BodyBufSubject, BodyBufSubject> CURRENT =
            AtomicReferenceFieldUpdater.newUpdater(BodyBufSubject.class, BodyBufSubject.class, "current");

    private final Scheduler scheduler;
    private int received;
    private volatile BodyBufSubject current = this;

    public BodyBufSubject() {
        this(Scheduler.current());
    }

    public BodyBufSubject(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
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

    @Override
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

    @Override
    public void onNext(ByteBuf value) {
        assert scheduler.inLoop();
        int i = value.readableBytes();
        if (i == 0) {
            value.release();
            return;
        }
        received += i;
        super.onNext(value);
    }

    @Override
    public void onError(Throwable error) {
        assert scheduler.inLoop();
        super.onError(error);
    }

    @Override
    public void onComplete() {
        assert scheduler.inLoop();
        super.onComplete();
    }

    @Override
    public Observable<ByteBuf> notifyOn(Scheduler scheduler) {
        if (scheduler == this.scheduler) {
            return this;
        }
        return super.notifyOn(scheduler);
    }

    public int getReceived() {
        return received;
    }

    public Observable<ByteBuf> fork() {
        if (current == null) {
            return ConsumedObservable.getInstance();
        }
        Forked forked = new Forked(scheduler);
        BodyBufSubject c;
        do {
            if ((c = current) == null) {
                return ConsumedObservable.getInstance();
            }
        } while (!CURRENT.compareAndSet(this, c, forked));
        c.subscribe0(forked);
        return forked.dumped;
    }

    private boolean isSubscribed0() {
        return super.isSubscribed();
    }

    @Override
    public boolean isSubscribed() {
        BodyBufSubject c = current;
        if (c != null) {
            return c.isSubscribed0();
        }
        return true;
    }

    private void subscribe0(Observer<? super ByteBuf> observer) {
        super.subscribe(observer);
    }

    @Override
    public void subscribe(Observer<? super ByteBuf> observer) {
        assert !(this instanceof Forked);
        BodyBufSubject c;
        do {
            if ((c = current) == null) {
                ConsumedObservable.<ByteBuf>getInstance().subscribe(observer);
                return;
            }
        } while (!CURRENT.compareAndSet(this, c, null));
        c.subscribe0(observer);
    }

    private boolean dismiss0() {
        return super.dismiss();
    }

    @Override
    public boolean dismiss() {
        BodyBufSubject c;
        do {
            if ((c = current) == null) {
                return false;
            }
        } while (!CURRENT.compareAndSet(this, c, null));
        return c.dismiss0();
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