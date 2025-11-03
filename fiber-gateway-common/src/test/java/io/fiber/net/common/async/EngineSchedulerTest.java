package io.fiber.net.common.async;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class EngineSchedulerTest {

    private EngineScheduler scheduler;

    @Before
    public void setUp() throws Exception {
        scheduler = EngineScheduler.init();
        scheduler.execute(this::mainTask);
    }

    private void mainTask() {
        System.out.println("-00=========");
        scheduler.schedule(() -> {
            System.out.println("100=========");
        }, 100);
        scheduler.schedule(() -> {
            System.out.println("200=========");
        }, 200);
        scheduler.schedule(() -> {
            System.out.println("300================");
        }, 300);

        List<Single<Integer>> list = IntStream.range(0, 10)
                .mapToObj(i -> Single.<Integer>create(emitter -> {
                    Scheduler.current().schedule(() -> emitter.onSuccess(i), i * 100L);
                })).collect(Collectors.toList());

        Single.zip(list, (x) -> Stream.of(x).mapToInt(i -> (Integer) i).sum())
                .subscribe((integer, throwable) -> {
                    Assert.assertNull(throwable);
                    System.out.println("sum:" + integer);
                    Assert.assertEquals(45, integer.intValue());
                    scheduler.schedule(() -> {
                        System.out.println("shutdown================");
                        scheduler.shutdown();
                    }, 300);
                });

    }

    @Test
    public void execute() {


        scheduler.runLoop();
    }
}