package io.fiber.net.support;

import io.fiber.net.common.utils.Predictions;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class CircuitBreakerStateMachine implements CircuitBreaker {

    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;
    private static final int STATE_HALF_OPEN = 2;

    private static final AtomicLongFieldUpdater<CircuitBreakerStateMachine> UPDATER = AtomicLongFieldUpdater.newUpdater(CircuitBreakerStateMachine.class, "state");

    private final String name;
    private final float failureRateThreshold;
    private final int halfOpenReqNum;
    private final long openWaitTimeMS;
    private final long startMS;

    private volatile long state;

    private final Metric closeMetric;
    private final Metric halfOpenMetric;

    public CircuitBreakerStateMachine(String name,
                                      float failureRateThreshold,
                                      int totalNumThreshold,
                                      int halfOpenReqNum,
                                      long openWaitTimeMS) {

        Predictions.assertTrue(openWaitTimeMS >= 10L, "open wait time MS >= 10");
        Predictions.assertTrue(halfOpenReqNum > 0, "half open num >= 10");
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        this.halfOpenReqNum = (int) (halfOpenReqNum * 1.5F);
        this.openWaitTimeMS = openWaitTimeMS;
        this.closeMetric = new Metric(totalNumThreshold);
        this.halfOpenMetric = new Metric(halfOpenReqNum);
        startMS = System.currentTimeMillis();
    }

    private long now() {
        return System.currentTimeMillis() - startMS;
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
                if (now() < brokenCycle(activatedState)) {
                    return true;
                }
                translateToHalfOpen(activatedState);
                return false;
            case STATE_HALF_OPEN: {
                return decrementHalfOpenPermits();
            }
            default:
                throw new IllegalStateException("unknown state:" + brokenState(activatedState));
        }
    }

    private boolean decrementHalfOpenPermits() {
        long activatedState;
        long available;
        do {
            if (brokenState(activatedState = UPDATER.get(this)) != STATE_HALF_OPEN
                    || (available = brokenCycle(activatedState)) <= 0) {
                return brokenState(activatedState) != STATE_CLOSE;
            }
        } while (!UPDATER.compareAndSet(this, activatedState, state(available - 1, STATE_HALF_OPEN)));
        return false;
    }

    private void translateToHalfOpen(long activatedState) {
        long update = state(halfOpenReqNum, STATE_HALF_OPEN);
        do {
            if (UPDATER.compareAndSet(this, activatedState, update)) {
                break;
            }
        } while (brokenState(activatedState = UPDATER.get(this)) == STATE_OPEN);
    }

    private void translateToClose() {
        long activatedState;
        do {
            if (brokenState(activatedState = UPDATER.get(this)) != STATE_HALF_OPEN) {
                return;
            }
        } while (!UPDATER.compareAndSet(this, activatedState, state(0, STATE_CLOSE)));
    }

    private void translateToOpen(int currentState) {
        assert currentState != STATE_OPEN;
        long deadline = now() + openWaitTimeMS;
        long activatedState;
        do {
            if (brokenState(activatedState = UPDATER.get(this)) != currentState) {
                return;
            }
        } while (!UPDATER.compareAndSet(this, activatedState, state(deadline, STATE_OPEN)));
    }

    @Override
    public void voteSuccess() {
        switch (brokenState(UPDATER.get(this))) {
            case STATE_CLOSE:
                closeMetric.record(false); // close -> open must be triggered after error
                break;
            case STATE_OPEN:
                break;
            case STATE_HALF_OPEN:
                translateFromHalfOpen(false);
                break;
        }
    }

    @Override
    public void voteError() {
        switch (brokenState(UPDATER.get(this))) {
            case STATE_CLOSE: {
                long recorded = closeMetric.record(true);
                int total = Metric.totalNum(recorded);
                int error = Metric.errorNum(recorded);
                if (total >= closeMetric.windowSize) {
                    if (error * 100.0f / total >= failureRateThreshold) {
                        translateToOpen(STATE_CLOSE);
                        closeMetric.reset();
                    }
                }
                break;
            }
            case STATE_OPEN:
                break;
            case STATE_HALF_OPEN:
                translateFromHalfOpen(true);
                break;
        }
    }

    private void translateFromHalfOpen(boolean err) {
        long recorded = halfOpenMetric.record(err);
        int total = Metric.totalNum(recorded);
        int error = Metric.errorNum(recorded);
        if (total >= halfOpenMetric.windowSize) {
            if (error * 100.0f / total < failureRateThreshold) {
                translateToClose();
            } else {
                translateToOpen(STATE_HALF_OPEN);
            }
            halfOpenMetric.reset();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    private static class Metric {

        private static int totalNum(long recorder) {
            return (int) (recorder & Integer.MAX_VALUE);
        }

        private static int errorNum(long recorder) {
            return (int) ((recorder >>> 32) & Integer.MAX_VALUE);
        }

        private final int windowSize;
        private int[] winData;

        private int reqNum;
        private int total;
        private int error;

        private Metric(int windowSize) {
            Predictions.assertTrue(windowSize > 1, "window size > 1");
            this.windowSize = windowSize;
            winData = new int[(windowSize + 31) >> 5];
        }

        private synchronized void reset() {
            reqNum = 0;
            total = 0;
            error = 0;
            Arrays.fill(winData, 0);
        }

        private long record(boolean err) {
            int t, e;
            synchronized (this) {
                int ws = windowSize;
                e = error;
                t = total;

                int raw = (reqNum = (reqNum + 1) & Integer.MAX_VALUE) % ws;
                int bit = 1 << (raw & 31);
                int winDatum = winData[raw = raw >> 5];

                if (t < ws) {
                    ++t;
                } else if ((winDatum & bit) != 0) {
                    --e;
                }
                if (err) {
                    ++e;
                    winDatum |= bit;
                } else {
                    winDatum &= ~bit;
                }
                total = t;
                error = e;
                winData[raw] = winDatum;
            }
            return (((long) e) << 32) | (t);
        }
    }

}
