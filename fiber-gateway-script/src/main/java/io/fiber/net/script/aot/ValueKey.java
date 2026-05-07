package io.fiber.net.script.aot;

import java.util.Arrays;

final class ValueKey {
    private final SsaKind kind;
    private final Op op;
    private final int index;
    private final int[] operands;

    private ValueKey(SsaKind kind, Op op, int index, int[] operands) {
        this.kind = kind;
        this.op = op;
        this.index = index;
        this.operands = operands;
    }

    static ValueKey forValue(SsaValue value) {
        int[] operands = new int[value.operands().size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = value.operands().get(i).id();
        }
        return forOperands(value, operands);
    }

    static ValueKey forValueNumbers(SsaValue value, int[] operands) {
        return forOperands(value, operands.clone());
    }

    private static ValueKey forOperands(SsaValue value, int[] operands) {
        if (SsaEffects.isCommutative(value) && operands.length == 2 && operands[0] > operands[1]) {
            int tmp = operands[0];
            operands[0] = operands[1];
            operands[1] = tmp;
        }
        Bytecode bytecode = value.bytecode();
        return new ValueKey(value.kind(), bytecode == null ? null : bytecode.op(), value.index(), operands);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValueKey)) {
            return false;
        }
        ValueKey key = (ValueKey) o;
        return index == key.index
                && kind == key.kind
                && op == key.op
                && Arrays.equals(operands, key.operands);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + (op == null ? 0 : op.hashCode());
        result = 31 * result + index;
        result = 31 * result + Arrays.hashCode(operands);
        return result;
    }
}
