package io.fiber.net.script.aot;

public enum EdgeKind {
    FALLTHROUGH,
    JUMP,
    COND_TRUE,
    COND_FALSE,
    EXCEPTION
}
