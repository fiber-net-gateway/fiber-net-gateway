package io.fiber.net.common.async;

import io.fiber.net.common.async.jct.MpscBlockingConsumerArrayQueue;
import io.netty.channel.EventLoop;
import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class EngineScheduler extends Scheduler {
    private static final Logger log = LoggerFactory.getLogger(EngineScheduler.class);
    private static final long START_TIME = System.nanoTime();

    public static long currentNanoTime() {
        return System.nanoTime() - START_TIME;
    }

    private static final Comparator<ScheduledFutureTask> SCHEDULED_FUTURE_TASK_COMPARATOR = ScheduledFutureTask::compareTo;

    private enum State {
        INITIALIZED, RUNNING, SUSPENDED, SHUTDOWN, ABORT
    }

    private final MpscBlockingConsumerArrayQueue<Runnable> queue = new MpscBlockingConsumerArrayQueue<>(16384);
    private final Thread thread = Thread.currentThread();
    private DefaultPriorityQueue<ScheduledFutureTask> scheduledTaskQueue;
    private long nextTaskId;
    private Throwable err;
    private State state = State.INITIALIZED;

    public static EngineScheduler init() {
        if (Scheduler.isInIOThread()) {
            throw new IllegalStateException("Cannot init engine scheduler in io thread");
        }

        EngineScheduler scheduler = new EngineScheduler();
        TH.set(scheduler);
        return scheduler;
    }

    private EngineScheduler() {
    }

    @Override
    public void execute(Runnable runnable) {
        queue.offer(runnable);
    }

    private ScheduledFuture schedule(final ScheduledFutureTask task) {
        if (inLoop()) {
            scheduleFromEventLoop(task);
        } else {
            execute(task);
        }
        return task;
    }

    void scheduleFromEventLoop(ScheduledFutureTask task) {
        scheduledTaskQueue().add(task.setId(++nextTaskId));
    }

    PriorityQueue<ScheduledFutureTask> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = new DefaultPriorityQueue<>(SCHEDULED_FUTURE_TASK_COMPARATOR,
                    // Use same initial capacity as java.util.PriorityQueue
                    11);
        }
        return scheduledTaskQueue;
    }

    final void removeScheduled(final ScheduledFutureTask task) {
        assert task.isCancelled();
        if (inLoop()) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            execute(task);
        }
    }

    static long deadlineNanos(long nanoTime, long delay) {
        long deadlineNanos = nanoTime + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    @Override
    public ScheduledFuture schedule(Runnable command, long timeoutMs) {
        if (timeoutMs < 0) {
            timeoutMs = 0;
        }

        long nanoTime = deadlineNanos(currentNanoTime(), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        return schedule(new ScheduledFutureTask(command, nanoTime, this));
    }

    @Override
    public ScheduledFuture scheduleInNano(Runnable command, long timeoutNano) {
        if (timeoutNano < 0) {
            timeoutNano = 0;
        }
        timeoutNano = deadlineNanos(currentNanoTime(), timeoutNano);
        return schedule(new ScheduledFutureTask(command, timeoutNano, this));
    }

    @Override
    public ScheduledFuture scheduleAtFixedRate(Runnable task, long initialDelay, long periodMs) {
        long deadlined = deadlineNanos(currentNanoTime(), TimeUnit.MILLISECONDS.toNanos(initialDelay));
        return schedule(new ScheduledFutureTask(task, deadlined, TimeUnit.MILLISECONDS.toNanos(periodMs), this));
    }

    @Override
    public boolean inLoop() {
        return Thread.currentThread() == thread;
    }

    @Override
    public EventLoop eventLoop() {
        return null;
    }

    public void runLoop() {
        if (!inLoop()) {
            throw new IllegalStateException("Not in loop");
        }
        if (state != State.INITIALIZED && state != State.SUSPENDED) {
            throw new IllegalStateException("engine scheduler is invalid status: " + state);
        }
        state = State.RUNNING;
        MpscBlockingConsumerArrayQueue<Runnable> queue = this.queue;

        do {
            addExpiredScheduledTasks(queue);
            Runnable task;
            long l = nextScheduledTaskNano();
            l = l < 0 ? Long.MAX_VALUE : l;
            try {
                task = queue.poll(l, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                continue;
            }

            while (task != null) {
                safeExecute(task);
                task = queue.poll();
            }

        } while (state == State.RUNNING);

        if (state == State.ABORT) {
            throw new IllegalStateException("engine scheduler is abort: " + err.getMessage(), err);
        }

    }

    protected final long nextScheduledTaskNano() {
        ScheduledFutureTask scheduledTask = peekScheduledTask();
        return scheduledTask != null ? scheduledTask.delayNanos() : -1;
    }

    protected static void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    final ScheduledFutureTask peekScheduledTask() {
        Queue<ScheduledFutureTask> scheduledTaskQueue = this.scheduledTaskQueue;
        return scheduledTaskQueue != null ? scheduledTaskQueue.peek() : null;
    }

    protected final Runnable pollScheduledTask(long nanoTime) {
        ScheduledFutureTask scheduledTask = peekScheduledTask();
        if (scheduledTask == null || scheduledTask.deadlineNanos() - nanoTime > 0) {
            return null;
        }
        scheduledTaskQueue.remove();
        scheduledTask.setConsumed();
        return scheduledTask;
    }

    private boolean addExpiredScheduledTasks(Queue<Runnable> queue) {
        if (scheduledTaskQueue == null || scheduledTaskQueue.isEmpty()) {
            return false;
        }
        long nanoTime = currentNanoTime();
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        if (scheduledTask == null) {
            return false;
        }
        do {
            queue.offer(scheduledTask);
        } while ((scheduledTask = pollScheduledTask(nanoTime)) != null);
        return true;
    }

    public void suspend() {
        if (!inLoop()) {
            throw new IllegalStateException("Not in loop");
        }
        execute(() -> state = State.SUSPENDED);
    }

    public void shutdown() {
        execute(() -> state = State.SHUTDOWN);
    }

    public void abort(Throwable err) {
        execute(() -> {
            this.err = err;
            state = State.ABORT;
        });
    }

    public boolean isTerminated() {
        return state == State.ABORT || state == State.SHUTDOWN;
    }

    public boolean isSuspended() {
        return state == State.SUSPENDED;
    }

    public void detach() {
        if (!inLoop()) {
            throw new IllegalStateException("Not in loop");
        }
        TH.remove();
    }
}
