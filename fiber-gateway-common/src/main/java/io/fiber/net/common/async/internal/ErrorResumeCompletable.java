package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Completable;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Function;

public class ErrorResumeCompletable implements Completable {
    private final Completable source;
    private final Function<? super Throwable, Completable> resume;

    public ErrorResumeCompletable(Completable source, Function<? super Throwable, Completable> resume) {
        this.source = source;
        this.resume = resume;
    }

    @Override
    public void subscribe(Observer observer) {
        source.subscribe(new Ob(resume, observer));
    }

    private static class Ob extends DisposableOb implements Observer {
        private final Function<? super Throwable, Completable> resume;
        private final Observer downStream;
        private Disposable upstream;
        private boolean secondary;

        private Ob(Function<? super Throwable, Completable> resume, Observer downStream) {
            this.resume = resume;
            this.downStream = downStream;
        }

        @Override
        public void onSubscribe(Disposable d) {
            Disposable u = this.upstream;
            this.upstream = d;
            if (u == null) {
                downStream.onSubscribe(this);
            }
        }

        @Override
        public boolean dispose() {
            boolean dispose = super.dispose();
            Disposable u;
            if (dispose && (u = upstream) != null) {
                dispose = u.dispose();
            }
            return dispose;
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
        }

        @Override
        public void onError(Throwable e) {
            if (isDisposed()) {
                return;
            }
            if (secondary) {
                downStream.onError(e);
                return;
            }

            secondary = true;
            Completable invoke;
            try {
                invoke = resume.invoke(e);
            } catch (Throwable ex) {
                downStream.onError(ex);
                return;
            }
            invoke.subscribe(this);
        }
    }
}
