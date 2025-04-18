package io.fiber.net.common.codec;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.BodyBufSubject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.FileRegion;
import io.netty.util.concurrent.FastThreadLocal;

public abstract class AbstractChannelConnection extends ChannelInboundHandlerAdapter implements UpgradedConnection {

    static class ConnHolder implements Runnable {
        AbstractChannelConnection head;
        AbstractChannelConnection tail;

        @Override
        public void run() {

        }

        void detach(AbstractChannelConnection conn) {
            AbstractChannelConnection prev = conn.prev, next = conn.next;
            ConnHolder holder = conn.holder;
            if (holder != this) {
                return;
            }

            if (prev != null) {
                prev.next = next;
            } else {
                head = next;
            }
            if (next != null) {
                next.prev = prev;
            } else {
                tail = prev;
            }
            conn.prev = conn.next = null;
            conn.holder = null;
        }

        void moveToHead(AbstractChannelConnection conn) {
            assert conn.holder == this;
            AbstractChannelConnection prev = conn.prev, next = conn.next;
            if (prev == null) {
                //already is head
                return;
            }
            prev.next = next;

            if (next != null) {
                next.prev = prev;
            } else {
                tail = prev;
            }
        }

        void putToHead(AbstractChannelConnection conn) {
            assert conn.holder == null || conn.holder == this;
            AbstractChannelConnection head = this.head;
            conn.holder = this;
            conn.next = head;
            conn.prev = null;
            this.head = conn;
            if (head == null) {
                assert tail == null;
                tail = conn;
            }
        }

    }

    static FastThreadLocal<ConnHolder> LOCAL = new FastThreadLocal<ConnHolder>() {
        @Override
        protected ConnHolder initialValue() {
            return new ConnHolder();
        }
    };

    private AbstractChannelConnection prev;
    private AbstractChannelConnection next;

    private final BodyBufSubject dataSubject;
    private final long timeoutMill;
    private final Channel ch;
    private ConnHolder holder;

    public AbstractChannelConnection(BodyBufSubject dataSubject,
                                     long timeoutMill,
                                     Channel ch) {
        this.dataSubject = dataSubject;
        this.timeoutMill = timeoutMill;
        this.ch = ch;
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
    public void channelRegistered(ChannelHandlerContext ctx) {
        assert dataSubject.getProducerScheduler().inLoop();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ConnHolder connHolder = holder;
        if (connHolder != null) {
            connHolder.detach(this);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (timeoutMill > 0) {
            LOCAL.get().moveToHead(this);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        ConnHolder connHolder = holder;
        if (connHolder != null) {
            connHolder.detach(this);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        ConnHolder connHolder = holder;
        if (connHolder != null) {
            connHolder.detach(this);
        }
    }

    @Override
    public void close() {
        ch.close();
    }

    @Override
    public void discardData() {
        dataSubject.dismiss();
    }

    @Override
    public Completable compClose() {
        return null;
    }

    @Override
    public Scheduler ioSchedule() {
        return dataSubject.getProducerScheduler();
    }

    @Override
    public void writeAndClose(FileRegion fileRegion) {

    }

    @Override
    public void writeAndClose(ByteBuf buf) {

    }

    @Override
    public Completable compWriteAndClose(FileRegion fileRegion) {
        return null;
    }

    @Override
    public Completable compWriteAndClose(ByteBuf buf) {
        return null;
    }

    @Override
    public void writeAndClose(Observable<ByteBuf> buf, boolean flush) {

    }

    @Override
    public long receivedDataLength() {
        return dataSubject.getReceived();
    }
}
