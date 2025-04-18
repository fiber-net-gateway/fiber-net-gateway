package io.fiber.net.common.codec;

import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class BrotliDecoder extends AbstractedCodec {

    private enum State {
        DONE, NEEDS_MORE_INPUT, ERROR
    }

    static {
        try {
            Brotli.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private final int inputBufferSize;
    private DecoderJNI.Wrapper decoder;


    /**
     * Creates a new BrotliDecoder with a default 8kB input buffer
     *
     * @param bufAcceptor xx
     */
    public BrotliDecoder(Consumer<? super ByteBuf> bufAcceptor) {
        this(8 * 1024, bufAcceptor);
    }

    /**
     * Creates a new BrotliDecoder
     *
     * @param inputBufferSize desired size of the input buffer in bytes
     * @param bufAcceptor     xx
     */
    public BrotliDecoder(int inputBufferSize, Consumer<? super ByteBuf> bufAcceptor) {
        super(bufAcceptor);
        this.inputBufferSize = ObjectUtil.checkPositive(inputBufferSize, "inputBufferSize");
    }

    private ByteBuf pull() {
        ByteBuffer nativeBuffer = decoder.pull();
        // nativeBuffer actually wraps brotli's internal buffer so we need to copy its content
        ByteBuf copy = ByteBufAllocator.DEFAULT.buffer(nativeBuffer.remaining());
        copy.writeBytes(nativeBuffer);
        return copy;
    }

    @Override
    protected void destroy() {
        super.destroy();
        if (decoder != null) {
            decoder.destroy();
            decoder = null;
        }
    }

    private State decompress(ByteBuf input) {
        for (; ; ) {
            switch (decoder.getStatus()) {
                case DONE:
                    return State.DONE;

                case OK:
                    decoder.push(0);
                    break;

                case NEEDS_MORE_INPUT:
                    if (decoder.hasOutput()) {
                        bufAcceptor.accept(pull());
                    }

                    if (!input.isReadable()) {
                        return State.NEEDS_MORE_INPUT;
                    }

                    ByteBuffer decoderInputBuffer = decoder.getInputBuffer();
                    decoderInputBuffer.clear();
                    int readBytes = readBytes(input, decoderInputBuffer);
                    decoder.push(readBytes);
                    break;

                case NEEDS_MORE_OUTPUT:
                    bufAcceptor.accept(pull());
                    break;

                default:
                    return State.ERROR;
            }
        }
    }

    private static int readBytes(ByteBuf in, ByteBuffer dest) {
        int limit = Math.min(in.readableBytes(), dest.remaining());
        ByteBuffer slice = dest.slice();
        slice.limit(limit);
        in.readBytes(slice);
        dest.position(dest.position() + limit);
        return limit;
    }


    @Override
    protected void doCodec(ByteBuf in) throws DataCodecException {
        if (decoder == null) {
            try {
                decoder = new DecoderJNI.Wrapper(inputBufferSize);
            } catch (Throwable e) {
                throw new DataCodecException("error create BR decoder", e);
            }
        }

        try {
            State state = decompress(in);
            if (state == State.DONE) {
                destroy();
            } else if (state == State.ERROR) {
                throw new DecompressionException("Brotli stream corrupted");
            }
        } catch (Exception e) {
            destroy();
            throw e;
        }
    }

}
