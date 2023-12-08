package io.fiber.net.script.ast;

import io.fiber.net.script.parse.Node;

public interface MaybeLValue extends Node {
    boolean isLValue();

    void markLValue();

}
