package io.fiber.net.common.utils;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueTaskExecutorFactoryTest {

    private QueueTaskExecutorFactory taskExecutorFactory;

    @Before
    public void b() {
        taskExecutorFactory = new QueueTaskExecutorFactory(4, 4, 100);
    }

    private static class Counter {
        private final CountDownLatch latch;
        final long size;
        long sum = 0;
        long last = -1;
        private final AtomicBoolean enter = new AtomicBoolean();

        private Counter(int size) {
            this.size = size;
            latch = new CountDownLatch(size);
        }

        void add(int i) {
            if (!enter.compareAndSet(false, true)) {
                throw new IllegalStateException("enter concurrent !!! ");
            }
            sum += i;
            long l = this.last;
            latch.countDown();
            last = Math.max(l, i);
            if (!enter.compareAndSet(true, false)) {
                throw new IllegalStateException("out concurrent !!! ");
            }
        }


        void await() throws InterruptedException {
            latch.await();
            Assert.assertEquals(last, 9999);
            Assert.assertEquals(sum, (last) * size / 2);

        }

    }

    @Test
    public void t() throws InterruptedException {
        QueueTaskExecutorFactory.Executor executor = taskExecutorFactory.create();
        ExecutorService executorService = Executors.newFixedThreadPool(12);
        Counter counter = new Counter(10000000);
        for (int i = 0; i < counter.size; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    executor.exec(() -> counter.add(finalI));
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            });
        }

        while (counter.latch.getCount() > 0) {
            System.out.println("======================:" + counter.latch.getCount());
            Thread.sleep(1000);
        }
    }

    @After
    public void e() {
        taskExecutorFactory.destroy();
    }
}
