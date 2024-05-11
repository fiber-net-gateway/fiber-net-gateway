package io.fiber.net.common.async;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.ThreadExecutorMap;

import java.util.concurrent.TimeUnit;

public abstract class Scheduler {
    private static final FastThreadLocal<IOScheduler> TH = new FastThreadLocal<>();

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
        IOScheduler scheduler = TH.getIfExists();
        if (scheduler == null) {
            EventExecutor currentExecutor = ThreadExecutorMap.currentExecutor();
            if (currentExecutor == null) {
                throw new IllegalStateException("not in io thread??");
            }
            scheduler = new IOScheduler(currentExecutor);
            TH.set(scheduler);
        }

        return scheduler;
    }

    public static Scheduler direct() {
        return DirectScheduler.INSTANCE;
    }

    protected Scheduler() {
    }

    public abstract void execute(Runnable runnable);

    public abstract ScheduledFuture<?> schedule(Runnable task, long timeoutMs);

    public abstract boolean inLoop();

    private static class IOScheduler extends Scheduler {
        private final EventExecutor eventExecutor;

        IOScheduler(EventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
        }

        @Override
        public void execute(Runnable runnable) {
            eventExecutor.execute(runnable);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long timeoutMs) {
            return eventExecutor.schedule(task, timeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean inLoop() {
            return eventExecutor.inEventLoop();
        }

    }

    private static class DirectScheduler extends Scheduler {
        private static final DirectScheduler INSTANCE = new DirectScheduler();

        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long timeoutMs) {
            return current().schedule(task, timeoutMs);
        }

        @Override
        public boolean inLoop() {
            return true;
        }
    }
}
