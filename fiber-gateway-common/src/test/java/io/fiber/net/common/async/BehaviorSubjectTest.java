package io.fiber.net.common.async;

import io.fiber.net.common.async.internal.BehaviorSubject;
import org.junit.Assert;
import org.junit.Test;

public class BehaviorSubjectTest {
    private static class Ob implements Observable.Observer<Integer> {
        final String name;
        Disposable d;
        int v = 0;

        private Ob(String name) {
            this.name = name;
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.d = d;
        }

        @Override
        public void onNext(Integer integer) {
            System.out.println(name + ": " + integer);
            v += integer;
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }
    }

    @Test
    public void v() {
        BehaviorSubject<Integer> behaviorSubject = new BehaviorSubject<>();
        Ob start = new Ob("start");
        Ob end = new Ob("end");
        behaviorSubject.subscribe(start);
        behaviorSubject.onNext(1);
        behaviorSubject.onNext(2);
        behaviorSubject.onNext(3);
        behaviorSubject.subscribe(end);
        behaviorSubject.onNext(4);
        Assert.assertEquals(10, start.v);
        start.d.dispose();
        behaviorSubject.onNext(5);
        Assert.assertEquals(12, end.v);
        Assert.assertEquals(10, start.v);
    }
}
