package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.utils.Assert;

public class JustCompletable implements Completable, Disposable {
    private static final JustCompletable SUC = new JustCompletable(null);

    public static JustCompletable ofSec() {
        return SUC;
    }

    public static JustCompletable ofErr(Throwable err) {
        Assert.notNull(err, "error is not present");
        return new JustCompletable(err);
    }

    private final Throwable err;

    private JustCompletable(Throwable err) {
        this.err = err;
    }


    @Override
    public void subscribe(Observer observer) {
        observer.onSubscribe(this);
        if (err != null) {
            observer.onError(err);
        } else {
            observer.onComplete();
        }
    }

    @Override
    public boolean isDisposed() {
        return true;
    }

    @Override
    public boolean dispose() {
        return false;
    }
}
