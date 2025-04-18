package io.fiber.net.common.codec;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.BodyBufSubject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ChannelUpgradeConnection extends ChannelUpgradeInput implements UpgradedConnection {
    private static final Logger log = LoggerFactory.getLogger(ChannelUpgradeConnection.class);
    private static final FiberException TIMEOUT = new FiberException("upgraded connection timeout", 504, "CONNECTION_TIMEOUT");

    private static class DataBufHandler extends ChannelInboundHandlerAdapter implements Runnable {
        private boolean closed;
        private final BodyBufSubject dataSubject;
        private final long timeout;
        private EventExecutor executor;
        private ScheduledFuture<?> schedule;
        private long expireTime;

        private DataBufHandler(BodyBufSubject dataSubject, long timeout) {
            this.dataSubject = dataSubject;
            this.timeout = timeout;
        }

        private void updateExpireTime() {
            long timeout;
            if ((timeout = this.timeout) <= 0) {
                return;
            }
            expireTime = System.currentTimeMillis() + timeout;
        }

        @Override
        public void run() {
            long d = this.expireTime - System.currentTimeMillis();
            if (d <= 0) {
                exceptionCaught(null, TIMEOUT);
            } else {
                schedule = executor.schedule(this, d, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            executor = ctx.executor();
            if (timeout > 0 && schedule == null && ctx.channel().isActive()) {
                updateExpireTime();
                schedule = executor.schedule(this, timeout, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            executor = ctx.executor();
            if (timeout > 0 && schedule == null ) {
                updateExpireTime();
                schedule = executor.schedule(this, timeout, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            executor = ctx.executor();
            if (timeout > 0 && schedule == null && ctx.channel().isActive()) {
                updateExpireTime();
                schedule = executor.schedule(this, timeout, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            stop();
            if (log.isDebugEnabled()) {
                log.debug("upgrade connection is closed: {}", ctx.channel().remoteAddress());
            }
        }

        private void stop() {
            if (!closed) {
                closed = true;
                dataSubject.onComplete();
            }
            ScheduledFuture<?> schedule = this.schedule;
            if (schedule != null) {
                schedule.cancel(false);
                this.schedule = null;
            }
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            stop();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            stop();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (closed) {
                ReferenceCountUtil.release(msg);
                return;
            }
            if (msg instanceof ByteBuf) {
                dataSubject.onNext((ByteBuf) msg);
                updateExpireTime();
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            closeWithErr(cause);
        }

        void closeWithErr(Throwable err) {
            if (!closed) {
                closed = true;
                dataSubject.onError(err);
            }
            ScheduledFuture<?> schedule = this.schedule;
            if (schedule != null) {
                schedule.cancel(false);
                this.schedule = null;
            }
        }

    }

    private final BodyBufSubject dataSubject;
    private final DataBufHandler dataBufHandler;

    public ChannelUpgradeConnection(Channel ch, Scheduler scheduler, long timeout) {
        super(ch, scheduler);
        this.dataSubject = new BodyBufSubject(scheduler);
        dataBufHandler = new DataBufHandler(dataSubject, timeout);
        EventLoop eventLoop = ch.eventLoop();
        if (eventLoop.inEventLoop()) {
            ch.pipeline().addLast(dataBufHandler);
        } else {
            eventLoop.execute(() -> ch.pipeline().addLast(dataBufHandler));
        }
    }

    @Override
    public Observable<ByteBuf> readDataUnsafe() {
        return dataSubject;
    }

    @Override
    public Observable<ByteBuf> peekData() {
        return dataSubject.fork();
    }


    @Override
    public void discardData() {
        dataSubject.dismiss();
    }

    @Override
    public long receivedDataLength() {
        return dataSubject.getReceived();
    }

    @Override
    protected void onInputErr(Throwable err) {
        BodyBufSubject dataSubject = this.dataSubject;
        if (dataSubject.isCompleted()) {
            return;
        }

        FiberException error = new FiberException("abort by input error:" + err.getMessage(), err, 400, "ABORT_BY_ERROR");
        if (dataSubject.getProducerScheduler().inLoop()) {
            dataBufHandler.closeWithErr(error);
        } else {
            dataSubject.getProducerScheduler().execute(() -> dataBufHandler.closeWithErr(error));
        }
    }
}
