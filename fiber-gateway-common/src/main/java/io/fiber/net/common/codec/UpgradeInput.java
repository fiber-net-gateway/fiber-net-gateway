package io.fiber.net.common.codec;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.FileRegion;

import java.util.function.Consumer;

public interface UpgradeInput {
    void close();

    Completable compClose();

    Scheduler ioSchedule();

    void writeAndClose(FileRegion fileRegion);

    void writeAndClose(ByteBuf buf);

    Completable compWriteAndClose(FileRegion fileRegion);

    Completable compWriteAndClose(ByteBuf buf);

    void writeAndClose(Observable<ByteBuf> buf, boolean flush);

    void addOnClose(Consumer<Throwable> err);
}
