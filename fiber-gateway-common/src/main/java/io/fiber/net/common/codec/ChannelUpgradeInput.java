package io.fiber.net.common.codec;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.DisposableOb;
import io.fiber.net.common.async.internal.ErrorObservable;
import io.fiber.net.common.utils.NoopBufObserver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.FileRegion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.util.function.Consumer;

public class ChannelUpgradeInput implements UpgradeInput {
    public static final FiberException CLOSE_EXP = new FiberException("closed upgraded connection", 495, "CLOSED_CONNECTION");
    public static final Completable ERR = Completable.error(CLOSE_EXP);
    public static final ErrorObservable<ByteBuf> ERROR_OBSERVABLE = new ErrorObservable<>(CLOSE_EXP);

    protected final Channel ch;
    protected final Scheduler scheduler;
    protected boolean closed;

    public ChannelUpgradeInput(Channel ch, Scheduler scheduler) {
        this.ch = ch;
        this.scheduler = scheduler;
    }

    private static class ListenerCompletable extends DisposableOb implements FutureListener<Void>, Completable {
        private Observer observer;
        private boolean completed;
        private Throwable err;

        @Override
        public void operationComplete(Future<Void> future) {
            completed = true;
            if (!future.isSuccess()) {
                err = future.cause();
            }
            if (observer != null) {
                notify0(observer);
            }
        }

        private void notify0(Observer observer) {
            if (err == null) {
                observer.onComplete();
            } else {
                observer.onError(err);
            }
        }

        @Override
        public void subscribe(Observer observer) {
            observer.onSubscribe(this);
            if (completed) {
                notify0(observer);
            } else {
                this.observer = observer;
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ch.close();
    }

    @Override
    public Completable compClose() {
        if (closed || !ch.isActive()) {
            closed = true;
            return Completable.success();
        }
        closed = true;
        ListenerCompletable completable = new ListenerCompletable();
        ch.close(ch.newPromise()).addListener(completable);
        return completable;
    }

    @Override
    public Scheduler ioSchedule() {
        return scheduler;
    }

    @Override
    public void writeAndClose(FileRegion fileRegion) {
        if (closed || !ch.isActive()) {
            closed = true;
            return;
        }
        closed = true;
        ch.writeAndFlush(fileRegion, ch.newPromise().addListener(ChannelFutureListener.CLOSE));
    }

    @Override
    public void writeAndClose(ByteBuf buf) {
        if (closed || !ch.isActive()) {
            closed = true;
            return;
        }
        closed = true;
        ch.writeAndFlush(buf, ch.newPromise().addListener(ChannelFutureListener.CLOSE));
    }

    @Override
    public Completable compWriteAndClose(FileRegion fileRegion) {
        return writeMsg(fileRegion);
    }

    private Completable writeMsg(Object fileRegion) {
        if (closed || !ch.isActive()) {
            closed = true;
            ReferenceCountUtil.release(fileRegion);
            return ERR;
        }

        closed = true;
        ChannelFuture future = ch.writeAndFlush(fileRegion);
        if (future.isDone()) {
            ch.close();
            if (future.isSuccess()) {
                return Completable.success();
            } else {
                return Completable.error(future.cause());
            }
        }

        future.addListener(ChannelFutureListener.CLOSE);
        return Completable.create(emitter -> future.addListener(f -> {
            if (f.isSuccess()) {
                emitter.onComplete();
            } else {
                emitter.onError(f.cause());
            }
        }));
    }

    @Override
    public Completable compWriteAndClose(ByteBuf buf) {
        return writeMsg(buf);
    }

    @Override
    public void writeAndClose(Observable<ByteBuf> buf, boolean flush) {
        if (closed || !ch.isActive()) {
            closed = true;
            buf.subscribe(NoopBufObserver.INSTANCE);
            return;
        }
        closed = true;
        buf.subscribe(new Ob(flush));
    }


    @Override
    public void addOnClose(Consumer<Throwable> err) {
        ch.closeFuture().addListener(future -> err.accept(future.cause()));
    }

    protected void onInputErr(Throwable err) {
    }

    private class Ob implements Observable.Observer<ByteBuf> {
        private final boolean flush;
        private Disposable d;

        private Ob(boolean flush) {
            this.flush = flush;
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.d = d;
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            Channel ch = ChannelUpgradeInput.this.ch;
            if (!ch.isActive()) {
                d.dispose();
                byteBuf.release();
                return;
            }
            if (byteBuf.readableBytes() == 0) {
                byteBuf.release();
                return;
            }
            if (flush) {
                ch.writeAndFlush(byteBuf, ch.voidPromise());
            } else {
                ch.write(byteBuf, ch.voidPromise());
            }
        }

        @Override
        public void onError(Throwable e) {
            onInputErr(e);
            ch.close();
        }

        @Override
        public void onComplete() {
            Channel ch = ChannelUpgradeInput.this.ch;
            if (!ch.isActive()) {
                return;
            }
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER, ch.newPromise().addListener(ChannelFutureListener.CLOSE));
        }
    }
}
