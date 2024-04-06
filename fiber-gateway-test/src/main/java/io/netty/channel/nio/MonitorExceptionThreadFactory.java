package io.netty.channel.nio;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;

public class MonitorExceptionThreadFactory extends DefaultThreadFactory {
    public MonitorExceptionThreadFactory(Class<?> poolType) {
        super(poolType);
    }

    public MonitorExceptionThreadFactory(String poolName) {
        super(poolName);
    }

    public MonitorExceptionThreadFactory(Class<?> poolType, boolean daemon) {
        super(poolType, daemon);
    }

    public MonitorExceptionThreadFactory(String poolName, boolean daemon) {
        super(poolName, daemon);
    }

    public MonitorExceptionThreadFactory(Class<?> poolType, int priority) {
        super(poolType, priority);
    }

    public MonitorExceptionThreadFactory(String poolName, int priority) {
        super(poolName, priority);
    }

    public MonitorExceptionThreadFactory(Class<?> poolType, boolean daemon, int priority) {
        super(poolType, daemon, priority);
    }

    public MonitorExceptionThreadFactory(String poolName, boolean daemon, int priority, ThreadGroup threadGroup) {
        super(poolName, daemon, priority, threadGroup);
    }

    public MonitorExceptionThreadFactory(String poolName, boolean daemon, int priority) {
        super(poolName, daemon, priority);
    }

    @Override
    protected Thread newThread(Runnable r, String name) {
        return new MonitorExceptionThread(threadGroup, r, name);
    }
}
