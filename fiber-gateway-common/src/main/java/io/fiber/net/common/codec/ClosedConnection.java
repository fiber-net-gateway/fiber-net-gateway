package io.fiber.net.common.codec;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.NoopBufObserver;
import io.netty.buffer.ByteBuf;
import io.netty.channel.FileRegion;

import java.util.function.Consumer;

public class ClosedConnection implements UpgradedConnection {


    public static final ClosedConnection INSTANCE = new ClosedConnection();


    @Override
    public Observable<ByteBuf> readDataUnsafe() {
        return ChannelUpgradeInput.ERROR_OBSERVABLE;
    }

    @Override
    public Observable<ByteBuf> peekData() {
        return ChannelUpgradeInput.ERROR_OBSERVABLE;
    }

    @Override
    public void close() {

    }

    @Override
    public void discardData() {

    }

    @Override
    public Completable compClose() {
        return Completable.success();
    }

    @Override
    public Scheduler ioSchedule() {
        return Scheduler.direct();
    }

    @Override
    public void writeAndClose(FileRegion fileRegion) {
        fileRegion.release();
    }

    @Override
    public void writeAndClose(ByteBuf buf) {
        buf.release();
    }

    @Override
    public Completable compWriteAndClose(FileRegion fileRegion) {
        fileRegion.release();
        return ChannelUpgradeInput.ERR;
    }

    @Override
    public Completable compWriteAndClose(ByteBuf buf) {
        buf.release();
        return ChannelUpgradeInput.ERR;
    }

    @Override
    public void writeAndClose(Observable<ByteBuf> buf, boolean flush) {
        buf.subscribe(NoopBufObserver.INSTANCE);
    }

    @Override
    public long receivedDataLength() {
        return 0;
    }

    @Override
    public void addOnClose(Consumer<Throwable> err) {
        err.accept(ChannelUpgradeInput.CLOSE_EXP);
    }

}
