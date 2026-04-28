package io.fiber.net.common.async;

import org.junit.Assert;
import org.junit.Test;

public class ObservableOnErrorResumeTest {

    @Test
    public void shouldResumeWithFallbackObservable() {
        RuntimeException err = new RuntimeException("origin");
        TestObserver<Integer> observer = new TestObserver<>();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onError(err);
        }).onErrorResume(e -> {
            Assert.assertSame(err, e);
            return Observable.create(emitter -> {
                emitter.onNext(2);
                emitter.onNext(3);
                emitter.onComplete();
            });
        }).subscribe(observer);

        Assert.assertEquals(6, observer.sum);
        Assert.assertTrue(observer.complete);
        Assert.assertNull(observer.error);
        Assert.assertEquals(1, observer.subscribeCount);
    }

    @Test
    public void shouldAbsorbErrorWithCompletedFallback() {
        TestObserver<Integer> observer = new TestObserver<>();

        Observable.<Integer>create(emitter -> emitter.onError(new RuntimeException("origin")))
                .onErrorResume(e -> Observable.create(Observable.Emitter::onComplete))
                .subscribe(observer);

        Assert.assertEquals(0, observer.sum);
        Assert.assertTrue(observer.complete);
        Assert.assertNull(observer.error);
    }

    @Test
    public void shouldEmitResumeFunctionError() {
        RuntimeException mapped = new RuntimeException("mapped");
        TestObserver<Integer> observer = new TestObserver<>();

        Observable.<Integer>create(emitter -> emitter.onError(new RuntimeException("origin")))
                .onErrorResume(e -> {
                    throw mapped;
                })
                .subscribe(observer);

        Assert.assertFalse(observer.complete);
        Assert.assertSame(mapped, observer.error);
    }

    private static class TestObserver<T extends Number> implements Observable.Observer<T> {
        int subscribeCount;
        int sum;
        Throwable error;
        boolean complete;

        @Override
        public void onSubscribe(Disposable d) {
            subscribeCount++;
        }

        @Override
        public void onNext(T t) {
            sum += t.intValue();
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
