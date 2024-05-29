package io.fiber.net.support;

import io.fiber.net.common.FiberException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;


public class CircuitBreakerTest {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerTest.class);

    @Test
    public void isBroken() throws Exception {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("aaaa",
                60, 100, 10, Duration.ofSeconds(1));
        for (int i = 0; i < 200; i++) {
            try {
                action(circuitBreaker, i >= 140);
            } catch (RuntimeException e) {
                log.error("error occur in {}", i);
                throw e;
            }
        }
        for (int i = 0; i < 1000; i++) {
            Assert.assertTrue(circuitBreaker.isBroken());
        }
        Thread.sleep(300);
        for (int i = 0; i < 1000; i++) {
            Assert.assertTrue(circuitBreaker.isBroken());
        }
        Thread.sleep(1000);

        for (int i = 0; i < 100; i++) {
            try {
                action(circuitBreaker, i < 5);
            } catch (RuntimeException e) {
                log.error("half open error occur in {}", i);
                throw e;
            }
        }
        Assert.assertFalse(circuitBreaker.isBroken());
    }

    @Test
    public void isBroken2() throws Exception {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("aaaa",
                60, 100, 10, Duration.ofSeconds(1));
        for (int i = 0; i < 200; i++) {
            try {
                action(circuitBreaker, i >= 140);
            } catch (RuntimeException e) {
                log.error("error occur in {}", i);
                throw e;
            }
        }
        Assert.assertTrue(circuitBreaker.isBroken());
        Thread.sleep(300);
        Assert.assertTrue(circuitBreaker.isBroken());
        Thread.sleep(1000);

        for (int i = 0; i < 10; i++) {
            try {
                action(circuitBreaker, i >= 4);
            } catch (RuntimeException e) {
                log.error("half open error occur in {}", i);
                throw e;
            }
        }

        for (int i = 0; i < 1000; i++) {
            Assert.assertTrue(circuitBreaker.isBroken());
        }
        Thread.sleep(300);
        for (int i = 0; i < 1000; i++) {
            Assert.assertTrue(circuitBreaker.isBroken());
        }
        Thread.sleep(1000);

        for (int i = 0; i < 100; i++) {
            try {
                action(circuitBreaker, false);
            } catch (RuntimeException e) {
                log.error("half open error occur in {}", i);
                throw e;
            }
        }

        Assert.assertFalse(circuitBreaker.isBroken());
    }

    private void action(CircuitBreaker circuitBreaker, boolean err) throws FiberException {
        if (circuitBreaker.isBroken()) {
            throw new RuntimeException("CIRCUIT_BROKEN");
        }
        if (err) {
            circuitBreaker.voteError();
        } else {
            circuitBreaker.voteSuccess();
        }
    }
}