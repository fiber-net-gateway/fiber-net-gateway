package io.fiber.net.support;

import io.fiber.net.common.utils.Predictions;

import java.time.Duration;

public interface CircuitBreaker {

    static CircuitBreaker of(String name,
                             float failureRateThreshold,
                             int totalNumThreshold,
                             int halfOpenReqNum,
                             Duration openWaitTime
    ) {
        Predictions.assertTrue(halfOpenReqNum > 0 && totalNumThreshold >= halfOpenReqNum,
                "require totalNumThreshold >= halfOpenReqNum");
        return new CircuitBreakerStateMachine(
                name,
                failureRateThreshold,
                totalNumThreshold,
                halfOpenReqNum,
                openWaitTime.toMillis()
        );
    }

    static CircuitBreaker of(String name) {
        return of(name, 60f, 100, 10, Duration.ofSeconds(60));
    }

    boolean isBroken();

    void voteSuccess();

    void voteError();

    String getName();
}
