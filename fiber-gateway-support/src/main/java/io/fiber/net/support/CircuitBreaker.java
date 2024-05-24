package io.fiber.net.support;

public interface CircuitBreaker {

    boolean isBroken();

    void voteSuccess();

    void voteError();
}
