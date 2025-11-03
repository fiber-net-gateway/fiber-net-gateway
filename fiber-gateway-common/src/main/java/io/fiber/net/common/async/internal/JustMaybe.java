package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Maybe;

public class JustMaybe<T, V extends T> implements Maybe<T> {
    private final V o;
    private final Throwable err;

    public JustMaybe(V o, Throwable err) {
        this.o = o;
        this.err = err;
    }

    @Override
    public void subscribe(Observer<? super T> observer) {
        SimpleDisposableOb ob = new SimpleDisposableOb();
        observer.onSubscribe(ob);
        if (ob.isDisposed()) {
            return;
        }
        if (err != null) {
            observer.onError(err);
        } else if (o != null) {
            observer.onSuccess(o);
        } else {
            observer.onComplete();
        }
    }
}
