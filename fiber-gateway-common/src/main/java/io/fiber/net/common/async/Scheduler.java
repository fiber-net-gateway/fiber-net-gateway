package io.fiber.net.common.async;

import io.netty.channel.EventLoop;
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

    public abstract ScheduledFuture<?> scheduleInNano(Runnable task, long timeoutNano);

    public abstract boolean inLoop();

    public abstract EventLoop eventLoop();

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

        @Override
        public ScheduledFuture<?> scheduleInNano(Runnable task, long timeoutNano) {
            return eventExecutor.schedule(task, timeoutNano, TimeUnit.NANOSECONDS);
        }

        public boolean inLoop() {
            return eventExecutor.inEventLoop();
        }

        @Override
        public EventLoop eventLoop() {
            EventExecutor e = eventExecutor;
            if (e instanceof EventLoop) {
                return (EventLoop) e;
            }
            return null;
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
        public ScheduledFuture<?> scheduleInNano(Runnable task, long timeoutNano) {
            return current().scheduleInNano(task, timeoutNano);
        }

        @Override
        public boolean inLoop() {
            return true;
        }

        @Override
        public EventLoop eventLoop() {
            return null;
        }
    }
}
