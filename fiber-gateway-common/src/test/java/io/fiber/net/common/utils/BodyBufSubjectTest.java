package io.fiber.net.common.utils;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Vector;
import java.util.concurrent.CountDownLatch;

public class BodyBufSubjectTest {
    private EventLoopGroup group;

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

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


    @Test
    public void t33() throws InterruptedException {
        Vector<ByteBuf> vector = new Vector<>(10000);
        Single<BodyBufSubject> bufSubjectSingle = Single.create(ob -> {
            group.execute(() -> {
                BodyBufSubject t = new BodyBufSubject();
                ob.onSuccess(t);
                ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
                for (int i = 1; i <= 5000; i++) {
                    int finalI = i;
                    t.getProducerScheduler().schedule(() -> {
                        ByteBuf buf = allocator.buffer(64);
                        buf.writeLong(12345678);
                        t.onNext(buf);
                        if (finalI == 5000) {
                            t.onComplete();
                            System.out.println("........");
                        }
                        vector.add(buf);
                    }, 10 + i);
                }
            });
        });

        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(bodyBufSubject::dismiss, 3000);
                System.out.println("end ....");
            });
        });
        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(() -> {
                    bodyBufSubject.notifyOn(Scheduler.current()).subscribe(NoopBufObserver.INSTANCE);
                    System.out.println("end .... 2222");
                }, 3000);
            });
        });
        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(() -> {
                    bodyBufSubject.toMaybe().notifyOn(Scheduler.current()).subscribe((buf, throwable1) -> {
                        buf.release();
                        System.out.println("end .... 333");
                    });
                }, 3000);

            });
        });

        Thread.sleep(8000);

        for (ByteBuf byteBuf : vector) {
            if (byteBuf.refCnt() > 0) {
                System.out.println("eeeeeee........");
            }
        }
        vector.clear();
        System.gc();
    }


    @Test
    public void t3() throws InterruptedException {
        Vector<ByteBuf> vector = new Vector<>(10000);
        Single<BodyBufSubject> bufSubjectSingle = Single.create(ob -> {
            group.execute(() -> {
                BodyBufSubject t = new BodyBufSubject();
                ob.onSuccess(t);
                ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
                for (int i = 1; i <= 5000; i++) {
                    int finalI = i;
                    t.getProducerScheduler().schedule(() -> {
                        ByteBuf buf = allocator.buffer(64);
                        buf.writeLong(12345678);
                        t.onNext(buf);
                        if (finalI == 5000) {
                            t.onError(new Exception("error"));
                            System.out.println("........");
                        }
                        vector.add(buf);
                    }, 10 + i);
                }
            });
        });

        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(bodyBufSubject::dismiss, 3000);
                System.out.println("end ....");
            });
        });
        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(() -> {
                    bodyBufSubject.notifyOn(Scheduler.current()).subscribe(NoopBufObserver.INSTANCE);
                    System.out.println("end .... 2222");
                }, 3000);
            });
        });
        group.execute(() -> {
            bufSubjectSingle.notifyOn(Scheduler.current()).subscribe((bodyBufSubject, throwable) -> {
                Scheduler.current().schedule(() -> {
                    bodyBufSubject.toMaybe().notifyOn(Scheduler.current()).subscribe((buf, throwable1) -> {
                        if (buf != null) {
                            buf.release();
                        }
                        System.out.println("end .... 333");
                    });
                }, 3000);

            });
        });

        Thread.sleep(8000);

        for (ByteBuf byteBuf : vector) {
            if (byteBuf.refCnt() > 0) {
                System.out.println("eeeeeee........");
            }
        }
        vector.clear();
        System.gc();
    }

    @Test
    public void t4() throws InterruptedException {
        group.execute(() -> {
            BodyBufSubject subject = new BodyBufSubject();
            subject.onNext(Unpooled.wrappedBuffer("123".getBytes()));
            subject.onError(new Exception("error"));
            Observable<ByteBuf> observable = subject.fork();
            Observable.Observer<ByteBuf> observer = new Observable.Observer<ByteBuf>() {
                @Override
                public void onSubscribe(Disposable d) {
                    System.out.println("onSubscribe");
                }

                @Override
                public void onNext(ByteBuf byteBuf) {
                    byteBuf.release();
                    System.out.println("onNext");
                }

                @Override
                public void onError(Throwable e) {
                    Assert.assertSame(Exception.class, e.getClass());
                    System.out.println("onError");
                }

                @Override
                public void onComplete() {
                    Assert.fail("onComplete should not found");
                }
            };
            observable.subscribe(observer);
            subject.subscribe(observer);
        });
    }

    @Test
    public void t5() throws InterruptedException {
        group.execute(() -> {
            BodyBufSubject subject = new BodyBufSubject();
            subject.onNext(Unpooled.wrappedBuffer("123".getBytes()));
            subject.onComplete();
            Observable<ByteBuf> observable = subject.fork();
            Observable.Observer<ByteBuf> observer = new Observable.Observer<ByteBuf>() {
                @Override
                public void onSubscribe(Disposable d) {
                    System.out.println("onSubscribe");
                }

                @Override
                public void onNext(ByteBuf byteBuf) {
                    byteBuf.release();
                    System.out.println("onNext");
                }

                @Override
                public void onError(Throwable e) {
                    Assert.fail("onError should not found");
                }

                @Override
                public void onComplete() {
                    System.out.println("onComplete");
                }
            };
            observable.subscribe(observer);
            subject.subscribe(observer);
        });
    }

    @Test
    public void t6() throws InterruptedException {

        final EventLoopGroup group = BodyBufSubjectTest.this.group;
        EventLoop loop1 = group.next();
        EventLoop loop2 = group.next();
        Assert.assertNotSame(loop1, loop2);
        class A {
            BodyBufSubject subject;

            void start() {
                loop1.execute(() -> {
                    subject = new BodyBufSubject();
                    subject.onNext(Unpooled.wrappedBuffer("123".getBytes()));
                    CountDownLatch latch = new CountDownLatch(1);
                    loop2.execute(() -> {
                        Observable<ByteBuf> observable = subject.fork();
                        Observable.Observer<ByteBuf> observer = new Observable.Observer<ByteBuf>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                System.out.println("onSubscribe");
                            }

                            @Override
                            public void onNext(ByteBuf byteBuf) {
                                byteBuf.release();
                                System.out.println("onNext");
                            }

                            @Override
                            public void onError(Throwable e) {
                                Assert.fail("onError should not found");
                            }

                            @Override
                            public void onComplete() {
                                System.out.println("onComplete");
                            }
                        };
                        observable.subscribe(observer);
                        subject.subscribe(observer);
                        latch.countDown();
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException ignore) {
                    }
                    subject.onComplete();
                    Scheduler.current().execute(() -> {
                    });
                });
            }
        }
        new A().start();
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