package io.fiber.net.script.aot;

import java.util.List;

final class SsaValueFactory {
    private int nextId;

    SsaValue create(SsaKind kind, SsaBlock block, Bytecode bytecode, int index, List<SsaValue> operands) {
        return new SsaValue(nextId++, kind, block, bytecode, index, operands);
    }

    int size() {
        return nextId;
    }
}
