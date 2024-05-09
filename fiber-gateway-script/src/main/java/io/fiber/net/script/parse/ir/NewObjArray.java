package io.fiber.net.script.parse.ir;

public class NewObjArray extends Exp {
    final boolean obj;

    public NewObjArray(boolean obj) {
        this.obj = obj;
    }

    public static NewObjArray of(boolean obj) {
        return new NewObjArray(obj);
    }

    public boolean isObj() {
        return obj;
    }

    @Override
    int assemble(ClzAssembler assembler) {
        if (isObj()) {
            assembler.newObj();
        } else {
            assembler.newArray();
        }
        return 1;
    }
}
