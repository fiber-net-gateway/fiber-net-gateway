package io.netty.channel.nio;

import io.netty.util.concurrent.FastThreadLocalThread;

public class MonitorExceptionThread extends FastThreadLocalThread {
    private Throwable ex;

    public MonitorExceptionThread(ThreadGroup threadGroup, Runnable r, String name) {
        super(threadGroup, r, name);
    }

    public static Throwable getAndClearExp() {
        Thread thread = Thread.currentThread();
        if (thread instanceof MonitorExceptionThread) {
            MonitorExceptionThread exceptionThread = (MonitorExceptionThread) thread;
            Throwable throwable = exceptionThread.ex;
            exceptionThread.ex = null;
            return throwable;
        }

        throw new IllegalStateException("error in MonitorExceptionThread");
    }

    private class WrapExpRunnableProxy implements Runnable {
        private final Runnable delegate;

        private WrapExpRunnableProxy(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } catch (Throwable e) {
                ex = e;
                throw e;
            }
        }
    }
}
