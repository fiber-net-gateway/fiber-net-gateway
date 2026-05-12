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
        SsaValue value;

        public Case(Block from, SsaValue value) {
            this.from = from;
            this.value = value;
        }
    }

    final List<Case> cases = new ArrayList<>();

    @Override
    public SsaValue.Type getResultType() {
        if (cases.isEmpty()) {
            return SsaValue.Type.Unknown;
        }
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
        value.addUsed(this);
    }

    public List<Case> getCases() {
        return cases;
    }

    public boolean removeCase(Block from) {
        for (int i = 0; i < cases.size(); i++) {
            Case aCase = cases.get(i);
            if (aCase.from == from) {
                aCase.value.removeUsed(this);
                cases.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        for (Case aCase : cases) {
            if (aCase.value == oldVal) {
                aCase.value = newVal;
                replaced++;
            }
        }
        return replaced;
    }
}
