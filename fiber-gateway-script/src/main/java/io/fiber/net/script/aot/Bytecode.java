package io.fiber.net.script.aot;

import io.fiber.net.script.run.InterpreterVm;

public final class Bytecode {
    public static final int ABSENT = -1;

    private final int pc;
    private final int raw;
    private final Op op;
    private final int operandIndex;
    private final int varIndex;
    private final int targetPc;
    private final int argCount;
    private final int iteratorIndex;
    private final int storeIndex;

    private Bytecode(int pc,
                     int raw,
                     Op op,
                     int operandIndex,
                     int varIndex,
                     int targetPc,
                     int argCount,
                     int iteratorIndex,
                     int storeIndex) {
        this.pc = pc;
        this.raw = raw;
        this.op = op;
        this.operandIndex = operandIndex;
        this.varIndex = varIndex;
        this.targetPc = targetPc;
        this.argCount = argCount;
        this.iteratorIndex = iteratorIndex;
        this.storeIndex = storeIndex;
    }

    public static Bytecode decode(int pc, int raw) {
        Op op = Op.fromCode(raw & 0xFF);
        switch (op) {
            case LOAD_CONST:
            case PROP_GET:
            case PROP_SET:
            case PROP_SET_1:
            case CALL_CONST:
            case CALL_ASYNC_CONST:
                return withOperand(pc, raw, op, raw >>> InterpreterVm.INSTRUMENT_LEN);

            case LOAD_VAR:
            case STORE_VAR:
            case ITERATE_INTO:
            case ITERATE_NEXT:
            case INTO_CATCH:
                return withVar(pc, raw, op, raw >>> InterpreterVm.INSTRUMENT_LEN);

            case JUMP:
            case JUMP_IF_FALSE:
            case JUMP_IF_TRUE:
                return withTarget(pc, raw, op, raw >>> InterpreterVm.INSTRUMENT_LEN);

            case CALL_FUNC:
            case CALL_ASYNC_FUNC:
                return new Bytecode(pc, raw, op,
                        raw >>> 16,
                        ABSENT,
                        ABSENT,
                        (raw >>> InterpreterVm.INSTRUMENT_LEN) & 0xFF,
                        ABSENT,
                        ABSENT);

            case CALL_FUNC_SPREAD:
            case CALL_ASYNC_FUNC_SPREAD:
                return new Bytecode(pc, raw, op,
                        raw >>> InterpreterVm.INSTRUMENT_LEN,
                        ABSENT,
                        ABSENT,
                        1,
                        ABSENT,
                        ABSENT);

            case ITERATE_KEY:
            case ITERATE_VALUE:
                return new Bytecode(pc, raw, op,
                        ABSENT,
                        ABSENT,
                        ABSENT,
                        ABSENT,
                        raw >>> InterpreterVm.ITERATOR_OFF,
                        (raw >>> InterpreterVm.INSTRUMENT_LEN) & InterpreterVm.MAX_ITERATOR_VAR);

            default:
                return new Bytecode(pc, raw, op, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT);
        }
    }

    public static Bytecode decode(int raw) {
        return decode(ABSENT, raw);
    }

    public static Bytecode[] decodeAll(int[] codes) {
        if (codes == null || codes.length == 0) {
            return new Bytecode[0];
        }
        Bytecode[] bytecodes = new Bytecode[codes.length];
        for (int i = 0; i < codes.length; i++) {
            bytecodes[i] = decode(i, codes[i]);
        }
        return bytecodes;
    }

    private static Bytecode withOperand(int pc, int raw, Op op, int operandIndex) {
        return new Bytecode(pc, raw, op, operandIndex, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT);
    }

    private static Bytecode withVar(int pc, int raw, Op op, int varIndex) {
        return new Bytecode(pc, raw, op, ABSENT, varIndex, ABSENT, ABSENT, ABSENT, ABSENT);
    }

    private static Bytecode withTarget(int pc, int raw, Op op, int targetPc) {
        return new Bytecode(pc, raw, op, ABSENT, ABSENT, targetPc, ABSENT, ABSENT, ABSENT);
    }

    public int pc() {
        return pc;
    }

    public int raw() {
        return raw;
    }

    public Op op() {
        return op;
    }

    public int opcode() {
        return op.code();
    }

    public int operandIndex() {
        return operandIndex;
    }

    public int varIndex() {
        return varIndex;
    }

    public int targetPc() {
        return targetPc;
    }

    public int argCount() {
        return argCount;
    }

    public int iteratorIndex() {
        return iteratorIndex;
    }

    public int storeIndex() {
        return storeIndex;
    }

    public boolean hasOperandIndex() {
        return operandIndex >= 0;
    }

    public boolean hasVarIndex() {
        return varIndex >= 0;
    }

    public boolean hasTargetPc() {
        return targetPc >= 0;
    }

    public boolean hasArgCount() {
        return argCount >= 0;
    }

    public boolean hasIteratorIndex() {
        return iteratorIndex >= 0;
    }

    public boolean hasStoreIndex() {
        return storeIndex >= 0;
    }

    public boolean isJump() {
        return op.isJump();
    }

    public boolean isUnconditionalJump() {
        return op.isUnconditionalJump();
    }

    public boolean isConditionalJump() {
        return op.isConditionalJump();
    }

    public boolean isTerminal() {
        return op.isTerminal();
    }

    public boolean isReturn() {
        return op.isReturn();
    }

    public boolean isThrow() {
        return op.isThrow();
    }

    public boolean isCall() {
        return op.isCall();
    }

    public boolean isFunctionCall() {
        return op.isFunctionCall();
    }

    public boolean isConstantCall() {
        return op.isConstantCall();
    }

    public boolean isAsync() {
        return op.isAsync();
    }

    public boolean isSpreadCall() {
        return op.isSpreadCall();
    }

    public boolean isVarLoad() {
        return op.isVarLoad();
    }

    public boolean isVarStore() {
        return op.isVarStore();
    }

    public boolean isVarAccess() {
        return op.isVarAccess();
    }

    public boolean mayThrow() {
        return op.mayThrow();
    }

    public int stackDelta() {
        if (op.isBinary()) {
            return -1;
        }
        if (op.isUnary()) {
            return 0;
        }
        switch (op) {
            case LOAD_CONST:
            case LOAD_ROOT:
            case LOAD_VAR:
            case NEW_OBJECT:
            case NEW_ARRAY:
            case CALL_CONST:
            case CALL_ASYNC_CONST:
            case ITERATE_NEXT:
                return 1;
            case DUMP:
                return 1;
            case POP:
            case STORE_VAR:
            case EXP_OBJECT:
            case EXP_ARRAY:
            case PUSH_ARRAY:
            case IDX_GET:
            case PROP_SET:
            case PROP_SET_1:
            case JUMP_IF_FALSE:
            case JUMP_IF_TRUE:
            case ITERATE_INTO:
            case THROW_EXP:
                return -1;
            case IDX_SET:
            case IDX_SET_1:
                return -2;
            case CALL_FUNC:
            case CALL_ASYNC_FUNC:
                return -argCount + 1;
            case CALL_FUNC_SPREAD:
            case CALL_ASYNC_FUNC_SPREAD:
                return 0;
            default:
                return 0;
        }
    }

    public Bytecode withPc(int pc) {
        if (this.pc == pc) {
            return this;
        }
        return new Bytecode(pc, raw, op, operandIndex, varIndex, targetPc, argCount, iteratorIndex, storeIndex);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(64);
        if (pc >= 0) {
            builder.append(pc).append(':');
        }
        builder.append(op.name());
        appendField(builder, "operand", operandIndex);
        appendField(builder, "var", varIndex);
        appendField(builder, "target", targetPc);
        appendField(builder, "argc", argCount);
        appendField(builder, "iterator", iteratorIndex);
        appendField(builder, "store", storeIndex);
        return builder.toString();
    }

    private static void appendField(StringBuilder builder, String name, int value) {
        if (value >= 0) {
            builder.append(' ').append(name).append('=').append(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bytecode)) {
            return false;
        }
        Bytecode bytecode = (Bytecode) o;
        return pc == bytecode.pc
                && raw == bytecode.raw
                && operandIndex == bytecode.operandIndex
                && varIndex == bytecode.varIndex
                && targetPc == bytecode.targetPc
                && argCount == bytecode.argCount
                && iteratorIndex == bytecode.iteratorIndex
                && storeIndex == bytecode.storeIndex
                && op == bytecode.op;
    }

    @Override
    public int hashCode() {
        int result = pc;
        result = 31 * result + raw;
        result = 31 * result + op.ordinal();
        result = 31 * result + operandIndex;
        result = 31 * result + varIndex;
        result = 31 * result + targetPc;
        result = 31 * result + argCount;
        result = 31 * result + iteratorIndex;
        result = 31 * result + storeIndex;
        return result;
    }
}
