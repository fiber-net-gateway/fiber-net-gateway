package io.fiber.net.support;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.test.TestInIOThreadParent;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;


public class RateLimiterTest extends TestInIOThreadParent {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterTest.class);

    @Test
    public void acquirePermission() throws InterruptedException {

        RateLimiter rateLimiter = RateLimiter.of("acquirePermission", Duration.ofSeconds(1), 3);

        Assert.assertTrue(rateLimiter.acquirePermission());
        Assert.assertTrue(rateLimiter.acquirePermission());
        Assert.assertTrue(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Thread.sleep(1000);
        Assert.assertEquals(0, rateLimiter.acquirePermission(2));
        Assert.assertTrue(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());

    }

    @Test
    public void acquirePermission2() throws InterruptedException {

        RateLimiter rateLimiter = RateLimiter.of("acquirePermission", Duration.ofMillis(1), 0);

        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Thread.sleep(10);
        Assert.assertTrue(rateLimiter.acquirePermission(2) > 0);
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());
        Assert.assertFalse(rateLimiter.acquirePermission());

    }

    @Test
    public void blockAcquirePermission() {
        RateLimiter rateLimiter = RateLimiter.of("blockAcquirePermission", Duration.ofSeconds(1), 3);
        Assert.assertTrue(blockingAcquire(rateLimiter, 2, "aaaa1"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa2"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa3"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa4"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa5"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa6"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa7"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa8"));
        Assert.assertTrue(blockingAcquire(rateLimiter, 1, "aaaa9"));
    }

    private static boolean blockingAcquire(RateLimiter rateLimiter, int perm, String eventName) {
        long l = rateLimiter.blockAcquirePermission(perm, Duration.ofSeconds(100));
        if (l == 0) {
            log.info("Acquire permission: {}/{} -> {}", eventName, perm, l);
            return true;
        }

        if (l > 0) {
            //log.info("starting permission: {}/{}", eventName, perm);
            Scheduler.current().scheduleInNano(() -> log.info("Acquire permission: {}/{}", eventName, perm), l);
            return true;
        }
        return false;
    }
}