package io.fiber.net.test;

import io.fiber.net.common.async.Scheduler;
import org.junit.Assert;
import org.junit.Test;

public class IOThreadRunnerTest extends TestInIOThreadParent {

    @Test
    public void t1() {
        System.out.println("adfadsfsa");
        Assert.assertTrue(Scheduler.current().inLoop());

        Scheduler.current().schedule(() -> {

            throw new IllegalStateException("error defer");

        }, 3000);
    }
}