package io.fiber.net.common.utils;

import io.fiber.net.common.ioc.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class QueueTaskExecutorFactory implements Destroyable {
    private static final Logger log = LoggerFactory.getLogger(QueueTaskExecutorFactory.class);

    private static class TaskWrap {
        private static final TaskWrap END = new TaskWrap(null);
        private static final AtomicReferenceFieldUpdater<TaskWrap, TaskWrap> UPDATER
                = AtomicReferenceFieldUpdater.newUpdater(TaskWrap.class, TaskWrap.class, "next");

        private final Runnable task;
        private volatile TaskWrap next;

        private TaskWrap(Runnable task) {
            this.task = task;
        }

        boolean addNext(TaskWrap task) {
            TaskWrap wrap = UPDATER.get(this);
            if (wrap != null) {
                return false;
            }
            return UPDATER.compareAndSet(this, null, task);
        }

        TaskWrap markEnd() {
            TaskWrap wrap = UPDATER.get(this);
            if (wrap != null) {
                return wrap;
            }
            if (UPDATER.compareAndSet(this, null, END)) {
                return null;
            }
            wrap = UPDATER.get(this);
            assert wrap != END;
            return wrap;
        }

    }

    public static class Executor implements Runnable {
        private static final AtomicReferenceFieldUpdater<Executor, TaskWrap> UPDATER
                = AtomicReferenceFieldUpdater.newUpdater(Executor.class, TaskWrap.class, "current");
        private static final AtomicReferenceFieldUpdater<Executor, TaskWrap> TAIL_UPDATER
                = AtomicReferenceFieldUpdater.newUpdater(Executor.class, TaskWrap.class, "tail");

        private final ExecutorService executorService;
        private volatile TaskWrap current;
        private volatile TaskWrap tail;


        public Executor(ExecutorService executorService) {
            this.executorService = executorService;
        }

        public void exec(Runnable task) {
            TaskWrap newWrap = new TaskWrap(task);
            TaskWrap taskWrap;
            do {
                taskWrap = TAIL_UPDATER.get(this);
            } while (!TAIL_UPDATER.compareAndSet(this, taskWrap, newWrap));

            if (taskWrap == null) {
                current = newWrap;
                executorService.execute(this);
                return;
            }

            if (taskWrap.addNext(newWrap)) {
                return;
            }

            assert taskWrap.next == TaskWrap.END;
            current = newWrap;
            executorService.execute(this);
        }

        @Override
        public void run() {
            TaskWrap first = this.current;
            assert first != null;
            TaskWrap current = first;
            TaskWrap next;
            for (; ; ) {

                try {
                    current.task.run();
                } catch (Throwable e) {
                    log.error("error run queue task", e);
                }

                if ((next = current.markEnd()) == null) {
                    break;
                }
                current = next;
            }

            UPDATER.compareAndSet(this, first, null);
            TAIL_UPDATER.compareAndSet(this, current, null);
        }

        public ExecutorService getExecutorService() {
            return executorService;
        }
    }

    private final ExecutorService executorService;

    public QueueTaskExecutorFactory(int coreThread, int maxThread, int queueSize) {
        executorService = new ThreadPoolExecutor(coreThread, maxThread, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize));
    }

    public Executor create() {
        return new Executor(executorService);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }

}
