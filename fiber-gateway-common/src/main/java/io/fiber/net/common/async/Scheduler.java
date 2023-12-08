package io.fiber.net.common.async;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.ThreadExecutorMap;

import java.util.concurrent.TimeUnit;

public class Scheduler {
    private static final FastThreadLocal<Scheduler> TH = new FastThreadLocal<>();

    public static void assertInIoThread() {
        if (ThreadExecutorMap.currentExecutor() == null) {
            throw new IllegalStateException("not in io thread??");
        }
    }

    public static boolean isInIOThread() {
        return ThreadExecutorMap.currentExecutor() != null;
    }

    public static boolean isInIOThread(EventLoopGroup group) {
        EventExecutor executor = ThreadExecutorMap.currentExecutor();
        return executor != null && executor.parent() == group;
    }

    public static Scheduler current() {
        Scheduler scheduler = TH.getIfExists();
        if (scheduler == null) {
            EventExecutor currentExecutor = ThreadExecutorMap.currentExecutor();
            if (currentExecutor == null) {
                throw new IllegalStateException("not in io thread??");
            }
            scheduler = new Scheduler(currentExecutor);
            TH.set(scheduler);
        }

        return scheduler;
    }

    public static void __setCurrentScheduler(Scheduler scheduler) {
        TH.set(scheduler);
    }

    private final EventExecutor eventExecutor;

    public Scheduler(EventExecutor eventExecutor) {
        this.eventExecutor = eventExecutor;
    }

    public void execute(Runnable runnable) {
        eventExecutor.execute(runnable);
    }

    public ScheduledFuture<?> schedule(Runnable task, long timeoutMs) {
        return eventExecutor.schedule(task, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean inLoop() {
        return eventExecutor.inEventLoop();
    }

    public EventExecutor getEventExecutor() {
        return eventExecutor;
    }
}
