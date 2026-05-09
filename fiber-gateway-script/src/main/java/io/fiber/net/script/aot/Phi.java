package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Phi extends Expr {

    protected Phi(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    public static class Case {
        final Block from;
        final SsaValue value;

        public Case(Block from, SsaValue value) {
            this.from = from;
            this.value = value;
        }
    }

    final List<Case> cases = new ArrayList<>();

    @Override
    public SsaValue.Type getResultType() {
        Iterator<Case> iterator = cases.iterator();
        SsaValue.Type type = iterator.next().value.getType();
        while (iterator.hasNext()) {
            Case next = iterator.next();
            if (next.value.getType() != type) {
                return SsaValue.Type.Unknown;
            }
        }

        return type;
    }

    public void addCase(Block from, SsaValue value) {
        cases.add(new Case(from, value));
    }

    public List<Case> getCases() {
        return cases;
    }
}
