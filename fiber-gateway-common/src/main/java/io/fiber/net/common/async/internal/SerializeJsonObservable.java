package io.fiber.net.common.async.internal;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.JsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.OutputStream;

public class SerializeJsonObservable implements Observable<ByteBuf> {
    public static final int DEF_INITIAL_CHUNK_SIZE = 256;
    public static final int DEF_MAX_CHUNK_SIZE = 32 * 1024;

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

    private class Ob extends OutputStream implements Runnable, Disposable {
        private final Observer<? super ByteBuf> observer;
        private final Scheduler scheduler;
        private boolean d;
        private ByteBuf buf;
        private int byteBlock = initialChunkSize;


        private Ob(Observer<? super ByteBuf> observer) {
            this.observer = observer;
            scheduler = observer.scheduler();
        }

        void serialize() {
            if (scheduler.inLoop()) {
                run();
            } else {
                scheduler.execute(this);
            }
        }

        @Override
        public void run() {
            if (d) {
                return;
            }
            ObjectMapper mapper = JsonUtil.MAPPER;
            try {
                mapper.writeValue(mapper.createGenerator(this, JsonEncoding.UTF8), object);
                observer.onComplete();
            } catch (Exception e) {
                observer.onError(e);
            } finally {
                close();
            }
        }

        private ByteBuf byteBuf() {
            ByteBuf buf = this.buf;
            if (buf != null) {
                if (buf.writerIndex() == buf.capacity()) {
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
        public void write(int b) {
            byteBuf().writeByte(b);
        }

        @Override
        public void write(byte[] b) {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) {
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
            if (buf != null) {
                observer.onNext(buf);
                buf = null;
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
