package io.fiber.net.common.utils;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class BodyBufSubjectTest {
    private EventLoopGroup group;

    @Before
    public void init() {
        group = EpollAvailable.workerGroup();
    }

    @After
    public void after() {
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    public void t1() throws InterruptedException {
        Single<BodyBufSubject> bufSubjectSingle = Single.create(ob -> {
            group.execute(() -> {
                BodyBufSubject t = new BodyBufSubject();
                ob.onSuccess(t);
                ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
                for (int i = 0; i < 10000; i++) {
                    ByteBuf buf = allocator.buffer(64);
                    buf.writeLong(12345678);
                    t.onNext(buf);
                }
                t.onComplete();
            });
        });

        CountDownLatch latch= new CountDownLatch(1);
        group.execute(() -> {
            Scheduler current = Scheduler.current();
            bufSubjectSingle.subscribe((bodyBufSubject, throwable) -> {
                bodyBufSubject.toMaybe().notifyOn(current).subscribe((byteBuf, throwable1) -> {
                    Assert.assertTrue(current.inLoop());
                    Assert.assertEquals(80000, byteBuf.readableBytes());
                    byteBuf.release();
                    latch.countDown();
                });
            });
        });
        latch.await();
    }

}