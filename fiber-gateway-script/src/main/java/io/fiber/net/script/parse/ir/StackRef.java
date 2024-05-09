package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.Assert;

import java.util.Arrays;

class StackRef extends Exp {
    Exp[] maybe = new Exp[4];
    int len;

    static StackRef of(Exp exp) {
        StackRef stackRef = new StackRef();
        stackRef.add(exp);
        return stackRef;
    }

    void add(Exp exp) {
        if (exp instanceof StackRef) {
            StackRef o = (StackRef) exp;
            Assert.isTrue(o.len > 0);
            for (int i = 0; i < o.len; i++) {
                add(o.maybe[i]);
            }
        } else {
            for (int i = 0; i < len; i++) {
                if (maybe[i] == exp) {
                    return;
                }
            }
            if (len >= maybe.length) {
                maybe = Arrays.copyOf(maybe, len << 1);
            }
            maybe[len++] = exp;
        }
    }

    @Override
    int assemble(ClzAssembler assembler) {
        throw new IllegalStateException("[BUG]not hit!!! stackRef is not instrument");
    }
}
