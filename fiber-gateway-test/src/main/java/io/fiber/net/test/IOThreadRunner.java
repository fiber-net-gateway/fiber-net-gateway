package io.fiber.net.test;

import io.netty.channel.nio.TestedNioEventLoop;
import io.netty.channel.nio.TestedNioEventLoopGroup;
import io.netty.util.internal.ThreadExecutorMap;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IOThreadRunner extends BlockJUnit4ClassRunner {
    private final TestedNioEventLoopGroup group;

    public IOThreadRunner(Class<?> testClz) throws InitializationError {
        super(testClz);
        group = new TestedNioEventLoopGroup();
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description);
        } else {
            runLeaf(new RunInNettyStatement(method), description, notifier);
        }
    }


    private class RunInNettyStatement extends Statement implements Runnable {
        private final FrameworkMethod method;
        private Statement statement;
        private Throwable e;
        private final CountDownLatch latch = new CountDownLatch(1);

        private RunInNettyStatement(FrameworkMethod method) {
            this.method = method;
        }

        @Override
        public void evaluate() throws Throwable {
            statement = methodBlock(method);
            group.execute(this);
            latch.await();
            if (e != null) {
                throw e;
            }
        }

        @Override
        public void run() {
            try {
                run0();
            } catch (Throwable ex) {
                e = ex;
                latch.countDown();
            }
        }

        private void run0() throws Throwable {
            TestedNioEventLoop eventExecutor = getEventExecutors();
            if (eventExecutor.pendingTasks() > 0 || eventExecutor.hasScheduleTask()) {
                throw new IllegalStateException("has task in io thread???");
            }
            statement.evaluate();
            testEnd();
        }


        private void testEnd() {
            TestedNioEventLoop eventExecutor = getEventExecutors();
            if (eventExecutor.pendingTasks() > 0) {
                eventExecutor.lazyExecute(this::testEnd);
            } else if (eventExecutor.hasScheduleTask()) {
                long maxTaskNanoDelay = eventExecutor.getMaxTaskNanoDelay();
                eventExecutor.schedule(this::testEnd, maxTaskNanoDelay, TimeUnit.NANOSECONDS);
            } else {
                latch.countDown();
            }
        }
    }

    private static TestedNioEventLoop getEventExecutors() {
        return (TestedNioEventLoop) ThreadExecutorMap.currentExecutor();
    }

}
