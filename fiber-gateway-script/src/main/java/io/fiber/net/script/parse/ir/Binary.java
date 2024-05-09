package io.fiber.net.script.parse.ir;

import io.fiber.net.script.run.Code;

class Binary extends Exp {

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.binary(getType(), isOptimiseIf());
        return -1;
    }

    enum Type {
        // HACK: int\s+BOP_(\w+)\s*=\s*\d+; 保持一致
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        MODULO,
        // ...
        MATCHES, //~
        LT, //<
        LTE, //<=
        GT, //~
        GTE, //~
        EQ, //~
        SEQ, //~
        NE, //~
        SNE, //~

        IN, // in
    }

    static final int MIN_CODE = Code.BOP_PLUS;
    static final int MAX_CODE = Code.BOP_IN;

    static final Type[] TYPES = Type.values();

    static {
        if (MIN_CODE + TYPES.length != MAX_CODE + 1) {
            throw new IllegalStateException("fix binary code");
        }
    }


    private final Type type;

    private final Exp left;
    private final Exp right;
    private boolean optimiseIf;

    Binary(Type type, Exp left, Exp right) {
        this.type = type;
        this.left = left;
        this.right = right;
    }

    Type getType() {
        return type;
    }

    Exp getLeft() {
        return left;
    }

    Exp getRight() {
        return right;
    }

    static Binary of(int code, Exp left, Exp right) {
        return new Binary(TYPES[code - MIN_CODE],
                left,
                right);
    }

    boolean canOptimiseIf() {
        return type.ordinal() >= Type.MATCHES.ordinal();
    }

    void setOptimiseIf() {
        this.optimiseIf = true;
    }

    boolean isOptimiseIf() {
        return optimiseIf;
    }
}
