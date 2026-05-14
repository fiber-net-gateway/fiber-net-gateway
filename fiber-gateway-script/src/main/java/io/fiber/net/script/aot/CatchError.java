package io.fiber.net.script.aot;

import java.util.List;

public class CatchError extends Expr {
    protected CatchError(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        SsaValue.Type t = null;
        for (Edge predecessor : belongTo.predecessors) {
            SsaValue.Type c = explicitThrowValue(predecessor);
            if (c == null) {
                continue;
            }
            if (t == null) {
                t = c;
            } else if (t != c) {
                return SsaValue.Type.Unknown;
            }
        }
        if (t != null) {
            return t;
        }
        return SsaValue.Type.EXCEPTION;
    }

    private static SsaValue.Type explicitThrowValue(Edge predecessor) {
        if (predecessor.type != Edge.Type.THROW) {
            throw new IllegalStateException("[bug]catch error instruction occur after jump/fallthrough ???");
        }
        List<Instruction> instructions = predecessor.predecessor.getInstructions();
        if (instructions.isEmpty()) {
            return null;
        }
        Instruction instruction = instructions.get(instructions.size() - 1);
        if (!(instruction instanceof io.fiber.net.script.aot.Throw)) {
            return SsaValue.Type.EXCEPTION;
        }
        return ((io.fiber.net.script.aot.Throw) instruction).value.getType();
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
