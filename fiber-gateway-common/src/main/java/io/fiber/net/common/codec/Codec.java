package io.fiber.net.common.codec;

import io.fiber.net.common.async.Disposable;
import io.netty.buffer.ByteBuf;

public interface Codec {

    default Disposable onSetDisposable(Disposable d) {
        return d;
    }

    void appendRaw(ByteBuf buf) throws DataCodecException;

    void appendEnd() throws DataCodecException;

    void abort();
}
