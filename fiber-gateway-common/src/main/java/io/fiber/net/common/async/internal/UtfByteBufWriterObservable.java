package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.utils.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.io.Writer;

public class UtfByteBufWriterObservable implements Observable<ByteBuf> {
    private final Consumer<Writer> writerConsumer;

    public UtfByteBufWriterObservable(Consumer<Writer> writerConsumer) {
        this.writerConsumer = writerConsumer;
    }

    private static class ObWriter extends Writer implements Disposable {
        private final Observer<? super ByteBuf> observer;
        private ByteBuf buf;
        private TmpCS tmpCS;
        private boolean disposed;

        private ByteBuf getBuf(int len) {
            ByteBuf buf;
            if ((buf = this.buf) == null) {
                buf = this.buf = ByteBufAllocator.DEFAULT.buffer(Math.max(len, 256));
            }
            if (buf.writableBytes() < len) {
                this.buf = null;
                if (buf.readableBytes() > 0) {
                    observer.onNext(buf);
                } else {
                    buf.release();
                }
                buf = this.buf = ByteBufAllocator.DEFAULT.buffer(Math.max(len, 256));
            }
            return buf;
        }

        public TmpCS getTmpCS() {
            if (tmpCS == null) {
                return tmpCS = new TmpCS();
            }
            return tmpCS;
        }

        private ObWriter(Observer<? super ByteBuf> observer) {
            this.observer = observer;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (disposed) {
                throw SerializeJsonObservable.ABORT_EXCEPTION;
            }
            TmpCS tmpCS = getTmpCS();
            tmpCS.reset(cbuf, off, len);
            ByteBufUtil.writeUtf8(getBuf((int) (len * 1.2) + 16), tmpCS);
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            if (disposed) {
                throw SerializeJsonObservable.ABORT_EXCEPTION;
            }
            ByteBufUtil.writeUtf8(getBuf((int) (len * 1.2) + 16), str, off, off + len);
        }

        @Override
        public void write(String str) throws IOException {
            if (disposed) {
                throw SerializeJsonObservable.ABORT_EXCEPTION;
            }
            super.write(str);
        }

        @Override
        public void write(int c) throws IOException {
            if (disposed) {
                throw SerializeJsonObservable.ABORT_EXCEPTION;
            }
            super.write(c);
        }

        @Override
        public void flush() throws IOException {
            if (disposed) {
                throw SerializeJsonObservable.ABORT_EXCEPTION;
            }

            ByteBuf buf;
            if ((buf = this.buf) != null && buf.readableBytes() > 0) {
                this.buf = null;
                observer.onNext(buf);
            }
        }


        @Override
        public void close() {
            ByteBuf buf;
            if ((buf = this.buf) != null) {
                this.buf = null;
                if (buf.readableBytes() > 0) {
                    observer.onNext(buf);
                } else {
                    buf.release();
                }
            }

            observer.onComplete();
        }


        @Override
        public boolean isDisposed() {
            return disposed;
        }


        @Override
        public boolean dispose() {
            if (buf != null) {
                buf.release();
                buf = null;
            }
            if (!disposed) {
                disposed = true;
            }
            return false;
        }
    }

    private static class TmpCS implements CharSequence {
        private char[] cbuf;
        private int off;
        private int len;

        private void reset(char[] cbuf, int off, int len) {
            this.cbuf = cbuf;
            this.off = off;
            this.len = len;
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return cbuf[off + index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            TmpCS obWriter = new TmpCS();
            obWriter.reset(cbuf, off + start, end - start);
            return obWriter;
        }
    }

    @Override
    public void subscribe(Observer<? super ByteBuf> observer) {
        ObWriter writer = new ObWriter(observer);
        observer.onSubscribe(writer);

        try {
            writerConsumer.accept(writer);
        } catch (SerializeJsonObservable.AbortException ignore) {
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            if (!writer.isDisposed()) {
                observer.onError(e);
            }
        } finally {
            writer.close();
        }
    }
}
