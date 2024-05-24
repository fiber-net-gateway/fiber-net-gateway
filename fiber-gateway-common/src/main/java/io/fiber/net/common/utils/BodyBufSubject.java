package io.fiber.net.common.utils;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.Subject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

public class BodyBufSubject extends Subject<ByteBuf> {
    private final Scheduler scheduler;
    private int received;

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
}