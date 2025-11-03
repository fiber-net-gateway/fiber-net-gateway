package io.fiber.net.common.utils;

import io.fiber.net.common.async.EngineScheduler;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.ext.EventSyncer;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubscribeSyncerTest {
    EngineScheduler scheduler;

    @Before
    public void before() {
        scheduler = EngineScheduler.init();
    }

    @Test
    public void test() throws Throwable {
        EventSyncer syncer = new EventSyncer();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
            System.out.println("prestop");
            scheduler.shutdown();
        });
        thread.start();
        syncer.trySync();
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isSuspended());
        System.out.println("init");
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isTerminated());
        System.out.println("stopped");
        thread.join();
    }

    private static class Run implements Runnable {
        private final Observable.Emitter<String> e;
        private final String name;
        private final int times;
        private int t;
        ScheduledFuture<?> future;

        private Run(Observable.Emitter<String> e, String name, int times) {
            this.e = e;
            this.name = name;
            this.times = times;
        }

        @Override
        public void run() {
            if (t >= times) {
                future.cancel(false);
                e.onComplete();
            }
            e.onNext(name + ": " + t++);
        }
    }

    @Test
    public void test2() throws Throwable {
        EventSyncer syncer = new EventSyncer();
        EventLoopGroup group = EpollAvailable.bossGroup();
        group.schedule(scheduler::shutdown, 600, TimeUnit.MILLISECONDS);

        syncer.syncedConsume(Observable.<String>create(emitter -> {
            Run a = new Run(emitter, "A", 10);
            a.future = group.scheduleAtFixedRate(a, 0, 50, TimeUnit.MILLISECONDS);
        }), System.out::println);
        syncer.syncedConsume(Observable.<String>create(emitter -> {
            Run a = new Run(emitter, "B", 4);
            a.future = group.scheduleAtFixedRate(a, 200, 100, TimeUnit.MILLISECONDS);
        }), System.out::println);
        syncer.trySync();
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isSuspended());
        System.out.println("init");
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isTerminated());
        System.out.println("stopped");
        group.shutdownGracefully().awaitUninterruptibly();
    }

    @Test
    public void test3() throws Throwable {
        EventSyncer syncer = new EventSyncer();
        EventLoopGroup group = EpollAvailable.bossGroup();
        group.schedule(scheduler::shutdown, 600, TimeUnit.MILLISECONDS);
        AtomicBoolean flag = new AtomicBoolean();

        syncer.syncedConsume(Observable.<String>create(emitter -> {
            Run a = new Run(emitter, "A", 10);
            a.future = group.scheduleAtFixedRate(a, 0, 50, TimeUnit.MILLISECONDS);
        }), t -> {
            System.out.println(t);
            if (!syncer.isSynced() && !flag.getAndSet(true)) {
                syncer.syncedConsume(Observable.<String>create(emitter -> {
                    Run a = new Run(emitter, "B", 4);
                    a.future = group.scheduleAtFixedRate(a, 200, 100, TimeUnit.MILLISECONDS);
                }), System.out::println);
            }
        });
        syncer.trySync();
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isSuspended());
        System.out.println("init");
        scheduler.runLoop();
        Assert.assertTrue(scheduler.isTerminated());
        System.out.println("stopped");
        group.shutdownGracefully().awaitUninterruptibly();
    }
}