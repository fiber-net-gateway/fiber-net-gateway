package io.fiber.net.script.parse.ir;

import io.fiber.net.script.run.Code;

class Unary extends Exp {


    enum Type {
        //int\s+UNARY_(\w+)\s*=\s*\d+;
        PLUS,
        MINUS,
        NEG,//!
        TYPEOF,//!
    }

    private static final int MIN_CODE = Code.UNARY_PLUS;
    private static final int MAX_CODE = Code.UNARY_TYPEOF;
    static final Type[] TYPES = Type.values();


    static {
        if (MIN_CODE + TYPES.length != MAX_CODE + 1) {
            throw new IllegalStateException("fix unary code");
        }
    }

    private final Type type;
    private final Exp exp;
    private boolean optimiseIf;

    Unary(Type type, Exp exp) {
        this.type = type;
        this.exp = exp;
    }

    Type getType() {
        return type;
    }

    Exp getExp() {
        return exp;
    }

    static Unary of(int code, Exp exp) {
        return new Unary(TYPES[code - MIN_CODE], exp);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.unary(getType(), isOptimiseIf());
        return 0;
    }

    void setOptimiseIf() {
        this.optimiseIf = true;
    }

    boolean isOptimiseIf() {
        return optimiseIf;
    }
}
