package io.fiber.net.common.codec;

import io.fiber.net.common.async.Disposable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.util.function.Consumer;

public abstract class AbstractedCodec implements Codec, Disposable {

    protected final Consumer<? super ByteBuf> bufAcceptor;
    private CompositeByteBuf remain;
    private Disposable disposable;
    private boolean destroyed;

    protected AbstractedCodec(Consumer<? super ByteBuf> consumer) {
        this.bufAcceptor = consumer;
    }

    @Override
    public Disposable onSetDisposable(Disposable d) {
        this.disposable = d;
        return this;
    }

    protected void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        if (remain != null) {
            remain.release();
            remain = null;
        }
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return destroyed;
    }

    @Override
    public boolean dispose() {
        boolean destroyed = this.destroyed;
        destroy();
        return !destroyed;
    }

    @Override
    public void appendRaw(ByteBuf in) throws DataCodecException {
        if (destroyed) {
            in.release();
            return;
        }

        CompositeByteBuf remain = this.remain;
        if (remain != null) {
            remain.addComponent(true, in);
            try {
                doCodec(remain);
            } catch (Throwable e) {
                destroy();
                throw e;
            }

            if (remain.readableBytes() == 0) {
                remain.release();
                this.remain = null;
            }

        } else {
            try {
                doCodec(in);
            } catch (Throwable e) {
                in.release();
                throw e;
            }
            if (in.readableBytes() != 0) {
                this.remain = remain = ByteBufAllocator.DEFAULT.compositeBuffer();
                remain.addFlattenedComponents(true, in);
            } else {
                in.release();
            }
        }
    }

    protected abstract void doCodec(ByteBuf in) throws DataCodecException;

    @Override
    public void appendEnd() throws DataCodecException {
        destroy();
    }

    @Override
    public void abort() {
        destroy();
    }
}
