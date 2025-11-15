package io.fiber.net.proxy.route;

import java.util.function.Consumer;

public interface VarConfigSource {
    int getVarIdx(VarType type, CharSequence key);

    void forEach(VarType type, Consumer<VarConst> consumer);

    int getVarLength();

    int getVarLength(VarType type);
}
