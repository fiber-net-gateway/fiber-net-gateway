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
        return of(name, 70f, 300, 30, Duration.ofSeconds(50));
    }

    boolean isBroken();

    void voteSuccess();

    void voteError();

    String getName();
}
