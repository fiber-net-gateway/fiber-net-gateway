package io.fiber.net.script.aot;

import java.util.List;

public class CatchError extends Expr {
    protected CatchError(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        SsaValue throwValue = null;
        for (Edge predecessor : belongTo.predecessors) {
            SsaValue value = explicitThrowValue(predecessor);
            if (value == null) {
                continue;
            }
            if (throwValue != null || belongTo.predecessors.size() > 1) {
                return SsaValue.Type.Unknown;
            }
            throwValue = value;
        }
        if (throwValue != null) {
            return throwValue.getType();
        }
        return SsaValue.Type.EXCEPTION;
    }

    private static SsaValue explicitThrowValue(Edge predecessor) {
        if (predecessor.type != Edge.Type.THROW) {
            return null;
        }
        List<Instruction> instructions = predecessor.predecessor.getInstructions();
        if (instructions.isEmpty()) {
            return null;
        }
        Instruction instruction = instructions.get(instructions.size() - 1);
        if (!(instruction instanceof io.fiber.net.script.aot.Throw)) {
            return null;
        }
        return ((io.fiber.net.script.aot.Throw) instruction).value;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
