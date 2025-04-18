package io.fiber.net.common.async.internal;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.JsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.IOException;
import java.io.OutputStream;

public class SerializeJsonObservable implements Observable<ByteBuf> {
    public static final int DEF_INITIAL_CHUNK_SIZE = 256;
    public static final int DEF_MAX_CHUNK_SIZE = 64 * 1024;

    private final Object object;
    private final ByteBufAllocator allocator;
    private final int initialChunkSize;
    private final int maxChunkSize;

    public SerializeJsonObservable(Object object, ByteBufAllocator allocator) {
        this(object, allocator, DEF_INITIAL_CHUNK_SIZE, DEF_MAX_CHUNK_SIZE);
    }

    private SerializeJsonObservable(Object object, ByteBufAllocator allocator, int initialChunkSize, int maxChunkSize) {
        this.object = object;
        this.allocator = allocator;
        this.initialChunkSize = initialChunkSize;
        // 128K
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public void subscribe(Observer<? super ByteBuf> observer) {
        Ob ob = new Ob(observer);
        observer.onSubscribe(ob);
        if (ob.isDisposed()) {
            return;
        }
        ob.serialize();
    }

    static class AbortException extends IOException {

        public AbortException(String message) {
            super(message);
        }


        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    static final AbortException ABORT_EXCEPTION = new AbortException("aborted write message");

    private class Ob extends OutputStream implements Disposable {
        private final Observer<? super ByteBuf> observer;
        private boolean d;
        private ByteBuf buf;
        private int byteBlock = initialChunkSize;


        private Ob(Observer<? super ByteBuf> observer) {
            this.observer = observer;
        }

        void serialize() {
            if (d) {
                return;
            }
            ObjectMapper mapper = JsonUtil.MAPPER;
            try {
                mapper.writeValue(mapper.createGenerator(this, JsonEncoding.UTF8), object);
                observer.onComplete();
            } catch (AbortException ignore) {
            } catch (Exception e) {
                if (!d) {
                    observer.onError(e);
                }
            } finally {
                close();
            }
        }

        private ByteBuf byteBuf() {
            ByteBuf buf;
            if ((buf = this.buf) != null) {
                if (buf.writerIndex() == buf.capacity()) {
                    this.buf = null;
                    observer.onNext(buf);
                    int block = byteBlock;
                    if (block < maxChunkSize) {
                        byteBlock = block <<= 1;
                    } else {
                        block = maxChunkSize;
                    }
                    this.buf = buf = allocator.buffer(block);
                }
                return buf;
            } else {
                return this.buf = allocator.buffer(byteBlock);
            }
        }

        @Override
        public void write(int b) throws AbortException {
            if (d) {
                throw ABORT_EXCEPTION;
            }
            byteBuf().writeByte(b);
        }

        @Override
        public void write(byte[] b) throws AbortException {
            if (d) {
                throw ABORT_EXCEPTION;
            }
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws AbortException {
            if (d) {
                throw ABORT_EXCEPTION;
            }
            int start = off;
            int end = off + len;
            do {
                ByteBuf byteBuf = byteBuf();
                int left = byteBuf.capacity() - byteBuf.writerIndex();
                int writeSize = Integer.min(left, end - start);
                byteBuf.writeBytes(b, start, writeSize);
                start += writeSize;
            } while (start < end);
        }

        @Override
        public void flush() {
            ByteBuf buf;
            if ((buf = this.buf) != null) {
                this.buf = null;
                observer.onNext(buf);
            }
        }

        @Override
        public void close() {
            if (buf != null) {
                buf.release();
                buf = null;
            }
        }

        @Override
        public boolean isDisposed() {
            return d;
        }

        @Override
        public boolean dispose() {
            if (d) {
                return false;
            }
            return d = true;
        }
    }
}
