package io.fiber.net.common.utils;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.netty.buffer.ByteBuf;

public class NoopBufObserver implements Observable.Observer<ByteBuf> {
    public static NoopBufObserver INSTANCE = new NoopBufObserver();

    private NoopBufObserver() {
    }

    @Override
    public void onSubscribe(Disposable d) {
        d.dispose();
    }

    @Override
    public void onNext(ByteBuf buf) {
        buf.release();
    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onComplete() {

    }
}