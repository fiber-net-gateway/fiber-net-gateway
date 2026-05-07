package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;

public enum Op {
    NOOP(Code.NOOP, false),
    LOAD_CONST(Code.LOAD_CONST, false),
    LOAD_ROOT(Code.LOAD_ROOT, false),
    DUMP(Code.DUMP, false),
    POP(Code.POP, false),
    LOAD_VAR(Code.LOAD_VAR, false),
    STORE_VAR(Code.STORE_VAR, false),

    NEW_OBJECT(Code.NEW_OBJECT, false),
    NEW_ARRAY(Code.NEW_ARRAY, false),
    EXP_OBJECT(Code.EXP_OBJECT, false),
    EXP_ARRAY(Code.EXP_ARRAY, false),
    PUSH_ARRAY(Code.PUSH_ARRAY, false),

    IDX_GET(Code.IDX_GET, false),
    IDX_SET(Code.IDX_SET, true),
    IDX_SET_1(Code.IDX_SET_1, true),
    PROP_GET(Code.PROP_GET, false),
    PROP_SET(Code.PROP_SET, true),
    PROP_SET_1(Code.PROP_SET_1, true),

    BOP_PLUS(Code.BOP_PLUS, true),
    BOP_MINUS(Code.BOP_MINUS, true),
    BOP_MULTIPLY(Code.BOP_MULTIPLY, true),
    BOP_DIVIDE(Code.BOP_DIVIDE, true),
    BOP_MOD(Code.BOP_MOD, true),
    BOP_MATCH(Code.BOP_MATCH, false),
    BOP_LT(Code.BOP_LT, false),
    BOP_LTE(Code.BOP_LTE, false),
    BOP_GT(Code.BOP_GT, false),
    BOP_GTE(Code.BOP_GTE, false),
    BOP_EQ(Code.BOP_EQ, false),
    BOP_SEQ(Code.BOP_SEQ, false),
    BOP_NE(Code.BOP_NE, false),
    BOP_SNE(Code.BOP_SNE, false),
    BOP_IN(Code.BOP_IN, false),

    UNARY_PLUS(Code.UNARY_PLUS, true),
    UNARY_MINUS(Code.UNARY_MINUS, true),
    UNARY_NEG(Code.UNARY_NEG, false),
    UNARY_TYPEOF(Code.UNARY_TYPEOF, false),

    CALL_FUNC(Code.CALL_FUNC, true),
    CALL_FUNC_SPREAD(Code.CALL_FUNC_SPREAD, true),
    CALL_ASYNC_FUNC(Code.CALL_ASYNC_FUNC, true),
    CALL_ASYNC_FUNC_SPREAD(Code.CALL_ASYNC_FUNC_SPREAD, true),
    CALL_CONST(Code.CALL_CONST, true),
    CALL_ASYNC_CONST(Code.CALL_ASYNC_CONST, true),

    JUMP(Code.JUMP, false),
    JUMP_IF_FALSE(Code.JUMP_IF_FALSE, false),
    JUMP_IF_TRUE(Code.JUMP_IF_TRUE, false),

    ITERATE_INTO(Code.ITERATE_INTO, false),
    ITERATE_NEXT(Code.ITERATE_NEXT, false),
    ITERATE_KEY(Code.ITERATE_KEY, false),
    ITERATE_VALUE(Code.ITERATE_VALUE, false),
    INTO_CATCH(Code.INTO_CATCH, false),

    THROW_EXP(Code.THROW_EXP, true),
    END_RETURN(Code.END_RETURN, false);

    private static final Op[] LOOKUP = initLookup();

    private final int code;
    private final boolean mayThrow;

    Op(int code, boolean mayThrow) {
        this.code = code;
        this.mayThrow = mayThrow;
    }

    public int code() {
        return code;
    }

    public boolean mayThrow() {
        return mayThrow;
    }

    public boolean isUnconditionalJump() {
        return this == JUMP;
    }

    public boolean isConditionalJump() {
        return this == JUMP_IF_FALSE || this == JUMP_IF_TRUE;
    }

    public boolean isJump() {
        return isUnconditionalJump() || isConditionalJump();
    }

    public boolean isReturn() {
        return this == END_RETURN;
    }

    public boolean isThrow() {
        return this == THROW_EXP;
    }

    public boolean isTerminal() {
        return isReturn() || isThrow();
    }

    public boolean isFunctionCall() {
        return this == CALL_FUNC || this == CALL_FUNC_SPREAD
                || this == CALL_ASYNC_FUNC || this == CALL_ASYNC_FUNC_SPREAD;
    }

    public boolean isConstantCall() {
        return this == CALL_CONST || this == CALL_ASYNC_CONST;
    }

    public boolean isCall() {
        return isFunctionCall() || isConstantCall();
    }

    public boolean isAsync() {
        return this == CALL_ASYNC_FUNC || this == CALL_ASYNC_FUNC_SPREAD || this == CALL_ASYNC_CONST;
    }

    public boolean isSpreadCall() {
        return this == CALL_FUNC_SPREAD || this == CALL_ASYNC_FUNC_SPREAD;
    }

    public boolean isVarLoad() {
        return this == LOAD_VAR;
    }

    public boolean isVarStore() {
        return this == STORE_VAR || this == ITERATE_INTO || this == ITERATE_KEY
                || this == ITERATE_VALUE || this == INTO_CATCH;
    }

    public boolean isVarAccess() {
        return isVarLoad() || isVarStore();
    }

    public boolean isBinary() {
        switch (this) {
            case BOP_PLUS:
            case BOP_MINUS:
            case BOP_MULTIPLY:
            case BOP_DIVIDE:
            case BOP_MOD:
            case BOP_MATCH:
            case BOP_LT:
            case BOP_LTE:
            case BOP_GT:
            case BOP_GTE:
            case BOP_EQ:
            case BOP_SEQ:
            case BOP_NE:
            case BOP_SNE:
            case BOP_IN:
                return true;
            default:
                return false;
        }
    }

    public boolean isUnary() {
        switch (this) {
            case UNARY_PLUS:
            case UNARY_MINUS:
            case UNARY_NEG:
            case UNARY_TYPEOF:
                return true;
            default:
                return false;
        }
    }

    public static Op fromCode(int code) {
        if (code >= 0 && code < LOOKUP.length) {
            Op op = LOOKUP[code];
            if (op != null) {
                return op;
            }
        }
        throw new IllegalArgumentException("unknown opcode: " + code);
    }

    private static Op[] initLookup() {
        Op[] lookup = new Op[Code.END_RETURN + 1];
        for (Op op : values()) {
            lookup[op.code] = op;
        }
        return lookup;
    }
}
