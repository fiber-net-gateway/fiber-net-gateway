package io.fiber.net.script.aot;

final class SsaEffects {
    private SsaEffects() {
    }

    static boolean isCopy(SsaValue value) {
        if (value.operands().size() != 1) {
            return false;
        }
        switch (value.kind()) {
            case DUP:
            case LOAD_VAR:
            case STORE_VAR:
                return true;
            default:
                return false;
        }
    }

    static boolean isRequiredRoot(SsaValue value) {
        Bytecode bytecode = value.bytecode();
        if (bytecode != null && bytecode.mayThrow()) {
            return true;
        }
        switch (value.kind()) {
            case INDEX_SET:
            case PROP_SET:
            case CALL:
            case ASYNC_CALL:
            case CONST_CALL:
            case ASYNC_CONST_CALL:
            case ITERATE_INTO:
            case ITERATE_NEXT:
            case ITERATE_VAR:
            case ERROR_TO_OBJ:
            case THROW:
            case RETURN:
                return true;
            default:
                return false;
        }
    }

    static boolean isControl(SsaValue value) {
        switch (value.kind()) {
            case BRANCH:
            case JUMP:
                return true;
            default:
                return false;
        }
    }

    static boolean isAdceRemovable(SsaValue value) {
        return isPure(value) || isControl(value);
    }

    static boolean isPure(SsaValue value) {
        Bytecode bytecode = value.bytecode();
        if (bytecode != null && bytecode.mayThrow()) {
            return false;
        }
        switch (value.kind()) {
            case UNDEFINED:
            case PHI:
            case EXCEPTION:
            case CONST:
            case ROOT:
            case LOAD_VAR:
            case STORE_VAR:
            case NEW_OBJECT:
            case NEW_ARRAY:
            case EXP_OBJECT:
            case EXP_ARRAY:
            case PUSH_ARRAY:
            case INDEX_GET:
            case PROP_GET:
            case BINARY:
            case UNARY:
            case DUP:
            case POP:
            case NOOP:
                return true;
            default:
                return false;
        }
    }

    static boolean isCommutative(SsaValue value) {
        Bytecode bytecode = value.bytecode();
        if (bytecode == null) {
            return false;
        }
        switch (bytecode.op()) {
            case BOP_PLUS:
            case BOP_MULTIPLY:
            case BOP_EQ:
            case BOP_SEQ:
            case BOP_NE:
            case BOP_SNE:
                return true;
            default:
                return false;
        }
    }
}
