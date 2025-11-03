package io.fiber.net.common.async;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class ScheduledFutureTask extends DefaultPromise<Void> implements Delayed, ScheduledFuture, PriorityQueueNode, Runnable {
    private long id;

    private long deadlineNanos;
    /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
    private final long periodNanos;
    private int queueIndex = INDEX_NOT_IN_QUEUE;
    private Runnable task;
    private final EngineScheduler executor;

    ScheduledFutureTask(Runnable runnable, long nanoTime, EngineScheduler executor) {
        this.task = runnable;
        deadlineNanos = nanoTime;
        this.executor = executor;
        periodNanos = 0;
    }

    ScheduledFutureTask(
            Runnable runnable, long nanoTime, long period, EngineScheduler executor) {

        this.task = runnable;
        deadlineNanos = nanoTime;
        periodNanos = validatePeriod(period);
        this.executor = executor;
    }


    private static long validatePeriod(long period) {
        if (period == 0) {
            throw new IllegalArgumentException("period: 0 (expected: != 0)");
        }
        return period;
    }

    ScheduledFutureTask setId(long id) {
        if (this.id == 0L) {
            this.id = id;
        }
        return this;
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    void setConsumed() {
        // Optimization to avoid checking system clock again
        // after deadline has passed and task has been dequeued
        if (periodNanos == 0) {
            deadlineNanos = 0L;
        }
    }

    public long delayNanos() {
        return delayNanos(EngineScheduler.currentNanoTime());
    }

    static long deadlineToDelayNanos(long currentTimeNanos, long deadlineNanos) {
        return deadlineNanos == 0L ? 0L : Math.max(0L, deadlineNanos - currentTimeNanos);
    }

    public long delayNanos(long currentTimeNanos) {
        return deadlineToDelayNanos(currentTimeNanos, deadlineNanos);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }

        ScheduledFutureTask that = (ScheduledFutureTask) o;
        long d = deadlineNanos() - that.deadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (id < that.id) {
            return -1;
        } else {
            assert id != that.id;
            return 1;
        }
    }

    private static final Runnable COMPLETED = new SentinelRunnable("COMPLETED");
    private static final Runnable CANCELLED = new SentinelRunnable("CANCELLED");
    private static final Runnable FAILED = new SentinelRunnable("FAILED");

    private static class SentinelRunnable implements Runnable {
        private final String name;

        SentinelRunnable(String name) {
            this.name = name;
        }

        @Override
        public void run() {
        } // no-op

        @Override
        public String toString() {
            return name;
        }
    }

    private boolean clearTaskAfterCompletion(boolean done, Runnable result) {
        if (done) {
            // The only time where it might be possible for the sentinel task
            // to be called is in the case of a periodic ScheduledFutureTask,
            // in which case it's a benign race with cancellation and the (null)
            // return value is not used.
            task = result;
        }
        return done;
    }


    protected final void setSuccessInternal() {
        super.setSuccess(null);
        clearTaskAfterCompletion(true, COMPLETED);
    }

    protected final void setFailureInternal(Throwable cause) {
        super.setFailure(cause);
        clearTaskAfterCompletion(true, FAILED);
    }


    @Override
    public void run() {
        try {
            if (delayNanos() > 0L) {
                // Not yet expired, need to add or remove from queue
                if (isCancelled()) {
                    scheduledExecutor().scheduledTaskQueue().removeTyped(this);
                } else {
                    scheduledExecutor().scheduleFromEventLoop(this);
                }
                return;
            }
            if (periodNanos == 0) {
                if (setUncancellable()) {
                    task.run();
                    setSuccessInternal();
                }
            } else {
                // check if is done as it may was cancelled
                if (!isCancelled()) {
                    task.run();
                    if (!executor.isTerminated()) {
                        if (periodNanos > 0) {
                            deadlineNanos += periodNanos;
                        } else {
                            deadlineNanos = EngineScheduler.currentNanoTime() - periodNanos;
                        }
                        if (!isCancelled()) {
                            scheduledExecutor().scheduledTaskQueue().add(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            setFailureInternal(cause);
        }
    }

    private EngineScheduler scheduledExecutor() {
        return executor;
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean canceled = clearTaskAfterCompletion(super.cancel(mayInterruptIfRunning), CANCELLED);
        if (canceled) {
            scheduledExecutor().removeScheduled(this);
        }
        return canceled;
    }


    boolean cancelWithoutRemove(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" deadline: ")
                .append(deadlineNanos)
                .append(", period: ")
                .append(periodNanos)
                .append(')');
    }

    @Override
    public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
        return queueIndex;
    }

    @Override
    public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
        queueIndex = i;
    }
}
