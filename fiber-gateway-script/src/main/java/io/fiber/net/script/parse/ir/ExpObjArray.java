package io.fiber.net.script.parse.ir;

class ExpObjArray extends Exp {
    final boolean obj;
    final Exp addition;

    ExpObjArray(boolean obj, Exp addition) {
        this.obj = obj;
        this.addition = addition;
    }

    static ExpObjArray of(boolean obj, Exp addition) {
        return new ExpObjArray(obj, addition);
    }

    boolean isObj() {
        return obj;
    }

    Exp getAddition() {
        return addition;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitExpObjArray(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        if (isObj()) {
            assembler.expObject();
        } else {
            assembler.expArray();
        }
        return -1;
    }
}
