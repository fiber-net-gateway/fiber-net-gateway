package io.fiber.net.common.codec;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.internal.DisposableOb;
import io.fiber.net.common.utils.NoopBufObserver;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpHeaderValues;

public class BufCodec {
    private static class CodecOb implements Observable.Observer<ByteBuf> {
        private final Observable.Observer<? super ByteBuf> downStream;
        private final Codec bufCodec;
        private Disposable d;

        private CodecOb(Observable.Observer<? super ByteBuf> downStream, Codec bufCodec) {
            this.downStream = downStream;
            this.bufCodec = bufCodec;
        }


        @Override
        public void onSubscribe(Disposable d) {
            this.d = d;
            this.downStream.onSubscribe(bufCodec.onSetDisposable(d));
        }

        @Override
        public void onNext(ByteBuf buf) {
            try {
                bufCodec.appendRaw(buf);
            } catch (DataCodecException e) {
                bufCodec.abort();
                d.dispose();
                downStream.onError(e);
            }
        }

        @Override
        public void onError(Throwable e) {
            bufCodec.abort();
            downStream.onError(e);
        }

        @Override
        public void onComplete() {
            try {
                bufCodec.appendEnd();
            } catch (DataCodecException e) {
                downStream.onError(e);
                return;
            }
            downStream.onComplete();
        }
    }

    private static class ZlibDecBufObservable implements Observable<ByteBuf> {

        private final Observable<ByteBuf> source;
        private final ZlibWrapper zlibWrapper;

        private ZlibDecBufObservable(Observable<ByteBuf> source, ZlibWrapper zlibWrapper) {
            this.source = source;
            this.zlibWrapper = zlibWrapper;
        }

        @Override
        public void subscribe(Observer<? super ByteBuf> observer) {
            ZlibDecoder zlibDecoder = new ZlibDecoder(zlibWrapper, true, observer::onNext);
            CodecOb deOb = new CodecOb(observer, zlibDecoder);
            source.subscribe(deOb);
        }
    }

    private static class BrotiDecBufObservable implements Observable<ByteBuf> {

        private final Observable<ByteBuf> source;

        private BrotiDecBufObservable(Observable<ByteBuf> source) {
            this.source = source;
        }

        @Override
        public void subscribe(Observer<? super ByteBuf> observer) {
            CodecOb codecOb = new CodecOb(observer, new BrotliDecoder(observer::onNext));
            source.subscribe(codecOb);
        }
    }

    private static class SnappyBufObservable implements Observable<ByteBuf> {

        private final Observable<ByteBuf> source;

        private SnappyBufObservable(Observable<ByteBuf> source) {
            this.source = source;
        }

        @Override
        public void subscribe(Observer<? super ByteBuf> observer) {
            CodecOb codecOb = new CodecOb(observer, new SnappyFrameDecoder(observer::onNext));
            source.subscribe(codecOb);
        }
    }

    private static class UnsupportedCodecBufObservable extends DisposableOb implements Observable<ByteBuf> {
        private final String contentEncoding;

        private UnsupportedCodecBufObservable(String encodeEncoding) {
            this.contentEncoding = encodeEncoding;
        }

        @Override
        public void subscribe(Observer<? super ByteBuf> observer) {
            observer.onSubscribe(this);
            if (!isDisposed()) {
                observer.onError(new DataCodecException("unsupported content encoding: " + contentEncoding));
            }
        }
    }

    public static Observable<ByteBuf> decode(Observable<ByteBuf> source, String contentEncoding) {
        if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) ||
                HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return new ZlibDecBufObservable(source, ZlibWrapper.GZIP);
        }
        if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
                HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            return new ZlibDecBufObservable(source, ZlibWrapper.ZLIB_OR_NONE);
        }
        if (Brotli.isAvailable() && HttpHeaderValues.BR.contentEqualsIgnoreCase(contentEncoding)) {
            return new BrotiDecBufObservable(source);
        }

        if (HttpHeaderValues.SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            return new SnappyBufObservable(source);
        }

        source.subscribe(NoopBufObserver.INSTANCE);
        return new UnsupportedCodecBufObservable(contentEncoding);
    }


}
