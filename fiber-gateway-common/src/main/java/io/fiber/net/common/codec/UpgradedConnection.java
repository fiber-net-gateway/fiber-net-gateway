package io.fiber.net.common.codec;

import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.netty.buffer.ByteBuf;

public interface UpgradedConnection extends UpgradeInput {

    Observable<ByteBuf> readDataUnsafe();

    default Observable<ByteBuf> readData() {
        return readDataUnsafe().notifyOn(Scheduler.current());
    }

    default Observable<ByteBuf> readData(Scheduler scheduler) {
        return readDataUnsafe().notifyOn(scheduler);
    }

    Observable<ByteBuf> peekData();

    void discardData();

    long receivedDataLength();

}
