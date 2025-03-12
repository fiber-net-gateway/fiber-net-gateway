package io.fiber.net.common.utils;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.util.ReferenceCounted;
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

        CountDownLatch latch = new CountDownLatch(4);
        group.execute(() -> {
            Scheduler current = Scheduler.current();
            bufSubjectSingle.subscribe((bodyBufSubject, throwable) -> {
                bodyBufSubject.fork().toMaybe().notifyOn(current).subscribe((byteBuf, throwable1) -> {
                    Assert.assertTrue(current.inLoop());
                    Assert.assertEquals(80000, byteBuf.readableBytes());
                    byteBuf.release();
                    latch.countDown();
                });
                bodyBufSubject.fork().notifyOn(current).toMaybe(
                        (a, b) -> {
                            if (a instanceof CompositeByteBuf) {
                                ((CompositeByteBuf) a).addFlattenedComponents(true, b);
                                return a;
                            } else {
                                CompositeByteBuf bufs = a.alloc().compositeBuffer();
                                bufs.addComponent(true, a);
                                bufs.addComponent(true, b);
                                return bufs;
                            }
                        }, ReferenceCounted::release).subscribe((byteBuf, throwable1) -> {
                    Assert.assertTrue(current.inLoop());
                    Assert.assertEquals(80000, byteBuf.readableBytes());
                    byteBuf.release();
                    latch.countDown();
                });
                bodyBufSubject.fork().notifyOn(current).toMaybe(
                        (a, b) -> {
                            if (a instanceof CompositeByteBuf) {
                                ((CompositeByteBuf) a).addFlattenedComponents(true, b);
                                return a;
                            } else {
                                CompositeByteBuf bufs = a.alloc().compositeBuffer();
                                bufs.addComponent(true, a);
                                bufs.addComponent(true, b);
                                return bufs;
                            }
                        }, ReferenceCounted::release).subscribe((byteBuf, throwable1) -> {
                    Assert.assertTrue(current.inLoop());
                    Assert.assertEquals(80000, byteBuf.readableBytes());
                    byteBuf.release();
                    latch.countDown();
                });
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


    @Test
    public void t2() throws InterruptedException {
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        ByteBuf buffer = allocator.buffer(100);
        ByteBuf buf = buffer.writeZero(100);
        System.out.println(buf.readableBytes());

        ByteBuf byteBuf = buffer.retainedSlice();
        System.out.println(byteBuf.readableBytes());

        buf.readLong();
        System.out.println(buf.readableBytes());
        System.out.println(byteBuf.readableBytes());
        byteBuf.release();

    }
}