package io.fiber.net.script.parse.ir;

class PushArray extends Exp {
    final Exp addition;

    PushArray(Exp addition) {
        this.addition = addition;
    }

    static PushArray of(Exp addition) {
        return new PushArray(addition);
    }

    Exp getAddition() {
        return addition;
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.pushArray();
        return -1;
    }
}
