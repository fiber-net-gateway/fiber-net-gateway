package io.fiber.net.support;

import io.fiber.net.common.utils.Predictions;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;


public class AtomicRateLimiter implements RateLimiter {
    private static final AtomicLongFieldUpdater<AtomicRateLimiter> UPDATER
            = AtomicLongFieldUpdater.newUpdater(AtomicRateLimiter.class, "state");

    private final long nanoTimeStart;
    private final String name;
    private final long refreshPeriod;
    private int permissionsPerCycle;

    @SuppressWarnings("unused")
    private long p0, p1, p2, p3, p4, p5, p6, p7;
    private volatile long state;
    @SuppressWarnings("unused")
    private long p10, p11, p12, p13, p14, p15, p16, p17;

    public AtomicRateLimiter(String name, long refreshPeriodNano, int limitForPeriod) {
        Predictions.assertTrue(limitForPeriod >= 0, "require limitForPeriod >= 0");
        Predictions.assertTrue(refreshPeriodNano >= 1000_000L, "min period < 1ms");
        this.name = name;
        this.refreshPeriod = refreshPeriodNano;// nano
        this.permissionsPerCycle = limitForPeriod;
        this.nanoTimeStart = System.nanoTime();
        this.state = state(0, limitForPeriod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeLimitForPeriod(final int limitForPeriod) {
        permissionsPerCycle = limitForPeriod;
    }

    /**
     * Calculates time elapsed from the class loading.
     */
    private long currentNanoTime() {
        return System.nanoTime() - nanoTimeStart;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long acquirePermission(final int permits) {
        return updateStateAndGetWait(permits, 0);
    }

    @Override
    public long blockAcquirePermission(int permits, Duration maxWait) {
        long nanos = maxWait.toNanos();
        long wait = updateStateAndGetWait(permits, nanos);
        if (wait <= 0) {
            return 0;
        }
        if (nanos >= wait) {
            return wait;
        }
        return -1;
    }


    @Override
    public void drainPermissions() {
        long activeState;
        long next;
        do {
            long currentNanos = currentNanoTime();
            long currentCycle = currentNanos / refreshPeriod;
            activeState = UPDATER.get(this);
            next = state(currentCycle, 0);
        } while (!UPDATER.compareAndSet(this, activeState, next));
    }

    private long updateStateAndGetWait(final int permits, long maxWait) {
        long cyclePeriodInNanos = refreshPeriod;
        int permissionsPerCycle = this.permissionsPerCycle;
        if (permissionsPerCycle == 0) {
            return Long.MAX_VALUE;
        }

        long nextNanosToWait;
        long activeState;
        long next;
        do {
            long currentNanos = currentNanoTime();
            long currentCycle = currentNanos / cyclePeriodInNanos;
            int nextPermissions = permissionToNext(currentCycle, activeState = UPDATER.get(this), permissionsPerCycle);
            nextNanosToWait = nanosToWaitForPermission(permits,
                    cyclePeriodInNanos,
                    permissionsPerCycle,
                    nextPermissions,
                    currentNanos,
                    currentCycle);
            next = state(currentCycle, maxWait >= nextNanosToWait ? nextPermissions - permits : nextPermissions);
        } while (!UPDATER.compareAndSet(this, activeState, next));
        return nextNanosToWait;
    }

    private static final long CYCLE_MASK = 0xFFFFFFFFL;

    private static long cycle(long activeState) {
        return activeState & CYCLE_MASK;
    }

    private static int permission(long activeState) {
        return (int) (activeState >>> 32);
    }

    private static long state(long cycle, int permission) {
        return ((long) permission << 32) | (CYCLE_MASK & cycle);
    }


    /**
     * Calculates time to wait for the required permits of permissions to get accumulated
     *
     * @param permits              permits of required permissions
     * @param cyclePeriodInNanos   current configuration values
     * @param permissionsPerCycle  current configuration values
     * @param availablePermissions currently available permissions, can be negative if some
     *                             permissions have been reserved
     * @param currentNanos         current time in nanoseconds
     * @param currentCycle         current {@link AtomicRateLimiter} cycle    @return nanoseconds to
     *                             wait for the next permission
     */
    private static long nanosToWaitForPermission(final int permits,
                                                 final long cyclePeriodInNanos,
                                                 final int permissionsPerCycle,
                                                 final int availablePermissions,
                                                 final long currentNanos,
                                                 final long currentCycle) {
        if (availablePermissions >= permits) {
            return 0L;
        }
        long nextCycleTimeInNanos = (currentCycle + 1) * cyclePeriodInNanos;
        long nanosToNextCycle = nextCycleTimeInNanos - currentNanos;
        int permissionsAtTheStartOfNextCycle = availablePermissions + permissionsPerCycle;
        int fullCyclesToWait = divCeil(permits - permissionsAtTheStartOfNextCycle, permissionsPerCycle);
        return (fullCyclesToWait * cyclePeriodInNanos) + nanosToNextCycle;
    }

    private static int permissionToNext(long currentCycle, long activeState, int permissionsPerCycle) {
        long prevCycle = cycle(activeState);
        int nextPermissions = permission(activeState);
        if (prevCycle != currentCycle) {
            long accumulatedPermissions = Math.abs(currentCycle - prevCycle) * permissionsPerCycle;
            nextPermissions = (int) Math.min(nextPermissions + accumulatedPermissions, permissionsPerCycle);
        }
        return nextPermissions;
    }

    /**
     * Divide two integers and round result to the bigger near mathematical integer.
     *
     * @param x - should be > 0
     * @param y - should be > 0
     */
    private static int divCeil(int x, int y) {
        return (x + y - 1) / y;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getAvailablePermissions() {
        int permissionsPerCycle = this.permissionsPerCycle;
        if (permissionsPerCycle == 0) {
            return 0;
        }
        return permissionToNext(currentNanoTime() / refreshPeriod, UPDATER.get(this), permissionsPerCycle);
    }


    @Override
    public String toString() {
        return "AtomicRateLimiter{" + "name='" + name + '\'' + '}';
    }
}
