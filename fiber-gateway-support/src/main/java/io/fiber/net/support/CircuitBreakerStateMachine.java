package io.fiber.net.support;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class CircuitBreakerStateMachine implements CircuitBreaker {

    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;
    private static final int STATE_HALF_OPEN = 2;

    private static final AtomicLongFieldUpdater<CircuitBreakerStateMachine> UPDATER
            = AtomicLongFieldUpdater.newUpdater(CircuitBreakerStateMachine.class, "state");
    private static final AtomicLongFieldUpdater<CircuitBreakerStateMachine> NOT_PERMITTED
            = AtomicLongFieldUpdater.newUpdater(CircuitBreakerStateMachine.class, "notPermittedNum");

    private final String name;
    private final float failureRateThreshold;
    private final float halfOpenRatio;
    private final int totalNumThreshold;
    private final long timeWindowNano;
    private final long startNano;

    private volatile long state;
    private volatile long notPermittedNum;

    // metric
    private static final AtomicLongFieldUpdater<CircuitBreakerStateMachine> TOTAL
            = AtomicLongFieldUpdater.newUpdater(CircuitBreakerStateMachine.class, "totalNum");
    private static final AtomicLongFieldUpdater<CircuitBreakerStateMachine> BULK_ID
            = AtomicLongFieldUpdater.newUpdater(CircuitBreakerStateMachine.class, "lastBulkCycle");
    private static final int BULK_SIZE = 16;
    private static final int BULK_MASK = BULK_SIZE - 1;

    private final AtomicLongArray window;

    private final long windowNano;
    private volatile long totalNum;
    private volatile long lastBulkCycle;


    private static long add(long num, int all, int error) {
        int errNum = ((int) (num >>> 32)) + error;
        int allNum = (int) (num & 0xFFFFFFFFL) + all;
        return ((long) errNum << 32) | ((long) allNum << 32);
    }

    private long recode(long currentNano, boolean error) {
        long currentBulk = currentNano / windowNano;
        int i = (int) (currentBulk & BULK_MASK);
        AtomicLongArray window = this.window;

        long lastBulk;
        long num, total, sub = 0;

        {
            long[] subs = null;
            while ((lastBulk = this.lastBulkCycle) < currentBulk) {
                int len = (int) Math.min(currentBulk - lastBulk, BULK_SIZE);
                if (subs == null) {
                    subs = new long[len];
                    for (int j = 0; j < len; j++) {
                        subs[j] = window.get((i - j + BULK_SIZE) & BULK_MASK);
                    }
                }
                if (BULK_ID.compareAndSet(this, lastBulk, currentBulk)) {
                    for (int j = 0; j < len; j++) {
                        long v;
                        if ((v = subs[j]) == 0) {
                            continue;
                        }
                        int a = (int) (v & 0xFFFFFFFFL);
                        int e = (int) (v >>> 32);
                        int idx = (i - j + BULK_SIZE) & BULK_MASK;
                        do {
                            v = add(num = window.get(idx), -a, -e);
                        } while (!window.compareAndSet(idx, num, v));
                        sub = add(sub, a, e);
                    }
                    break;
                }
            }
        }
        int a = -(int) (sub & 0xFFFFFFFFL) + 1;
        int e = -(int) (sub >>> 32) + (error ? 1 : 0);
        do {
            total = add(num = TOTAL.get(this), a, e);
        } while (!TOTAL.compareAndSet(this, num, total));
        do {
            sub = add(num = window.get(i), a, e);
        } while (!window.compareAndSet(i, num, sub));
        return total;
    }

    public CircuitBreakerStateMachine(String name,
                                      float failureRateThreshold,
                                      float halfOpenRatio,
                                      int totalNumThreshold,
                                      long timeWindowNano) {
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        this.halfOpenRatio = halfOpenRatio;
        this.totalNumThreshold = totalNumThreshold;
        this.timeWindowNano = timeWindowNano;
        this.windowNano = timeWindowNano / BULK_SIZE;
        this.window = new AtomicLongArray(BULK_SIZE);
        this.startNano = System.nanoTime();
    }

    private long currentNano() {
        return System.nanoTime() - startNano;
    }

    private static final int CYCLE_BIT_NUM = 60;
    private static final long CYCLE_MASK = (1L << CYCLE_BIT_NUM) - 1L;
    private static final int STATE_BIT_NUM = 64 - CYCLE_BIT_NUM - 1;
    private static final long STATE_MASK = (1 << STATE_BIT_NUM) - 1;

    private static long state(long cycle, int state) {
        return ((STATE_MASK & state) << CYCLE_BIT_NUM) | (cycle & CYCLE_MASK);
    }

    private static int brokenState(long state) {
        return (int) (state >>> CYCLE_BIT_NUM);
    }

    private static long brokenCycle(long state) {
        return state & CYCLE_MASK;
    }


    @Override
    public boolean isBroken() {

        long activatedState;
        switch (brokenState(activatedState = UPDATER.get(this))) {
            case STATE_CLOSE:
                return false;
            case STATE_OPEN:
                NOT_PERMITTED.incrementAndGet(this);
                return true;
            case STATE_HALF_OPEN: {
                // TODO

                return true;
            }
            default:
                throw new IllegalStateException("unknown state:" + brokenState(activatedState));
        }
    }

    @Override
    public void voteSuccess() {
        long currentNano = currentNano();
        long recode = recode(currentNano, false);

    }

    @Override
    public void voteError() {
        long currentNano = currentNano();
        long recode = recode(currentNano, true);
    }
}
