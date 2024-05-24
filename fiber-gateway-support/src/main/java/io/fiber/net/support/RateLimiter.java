package io.fiber.net.support;

import io.fiber.net.common.utils.Assert;

import java.time.Duration;

/**
 * A RateLimiter instance is thread-safe can be used to decorate multiple requests.
 * <p>
 * A RateLimiter distributes permits at a configurable rate. {@link #acquirePermission()} blocks if
 * necessary until a permit is available, and then takes it. Once acquired, permits need not be
 * released.
 */
public interface RateLimiter {

    /**
     * Creates a RateLimiter with a custom RateLimiter configuration.
     *
     * @param name the name of the RateLimiter
     * @return The {@link RateLimiter}
     */
    static RateLimiter of(String name, Duration refreshPeriod, int limitForPeriod) {
        Assert.isTrue(limitForPeriod >= 0, "limitForPeriod >= 0");
        Assert.isTrue(Duration.ofMillis(1L).compareTo(refreshPeriod) <= 0, "refreshPeriod must be >= 1ms");
        return new AtomicRateLimiter(name, refreshPeriod.toNanos(), limitForPeriod);
    }

    /**
     * Dynamic rate limiter configuration change. This method allows to change count of permissions
     * available during refresh period. NOTE! New limit won't affect current period permissions and
     * will apply only from next one.
     *
     * @param limitForPeriod new permissions limit
     */
    void changeLimitForPeriod(int limitForPeriod);

    default boolean acquirePermission() {
        return acquirePermission(1) == 0;
    }

    long acquirePermission(int permits);

    default long blockAcquirePermission(Duration maxWait) {
        return blockAcquirePermission(1, maxWait);
    }

    /**
     * <pre>
     * class BlockingRL {
     *     RateLimiter rl = RateLimiter.of("xxx", Duration.ofSeconds(1), 3);
     *     boolean acquire(Duration maxWait) {
     *         long w = rl.blockAcquirePermission(1, maxWait);
     *         if (w == 0) {
     *             return true;
     *         } else if (l > 0) {
     *             LockSupport.parkNanos(l);
     *             return true;
     *         } else {
     *             return false;
     *         }
     *     }
     * }
     * </pre>
     *
     * @param permits acquired permits
     * @param maxWait caller's acceptable max wait time
     * @return 0 if available now. positive number in Nano-Seconds for waiting and acquired. negative for failed acquiring
     */
    long blockAcquirePermission(int permits, Duration maxWait);

    /**
     * Drains all the permits left in the current period.
     */
    void drainPermissions();

    /**
     * Get the name of this RateLimiter
     *
     * @return the name of this RateLimiter
     */
    String getName();

    int getAvailablePermissions();
}
