package io.fiber.net.proxy.lib;

import io.fiber.net.script.Library;

public interface SyncHttpFunc extends Library.Function {
    @Override
    default boolean isConstExpr() {
        return false;
    }

}
