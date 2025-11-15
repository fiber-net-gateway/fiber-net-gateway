package io.fiber.net.proxy.route;

import io.fiber.net.script.Library;

public interface SyncHttpConst extends Library.Constant {
    @Override
    default boolean isConstExpr() {
        return false;
    }
}
