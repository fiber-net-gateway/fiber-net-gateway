package io.fiber.net.script.aot;

public class DeadCodeElimination {

    private final Cfg cfg;

    public DeadCodeElimination(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        boolean localChanged;
        do {
            localChanged = false;
            for (Block block : cfg.getBlocks()) {
                for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                    if (!isDead(instruction)) {
                        continue;
                    }
                    instruction.dropOperands();
                    block.removeInstruction(instruction);
                    localChanged = true;
                    changed = true;
                }
            }
        } while (localChanged);
        return changed;
    }

    static boolean isDead(Instruction instruction) {
        if (!(instruction instanceof Expr)) {
            return false;
        }
        Expr expr = (Expr) instruction;
        return expr.getResult().getUsedCount() == 0 && expr.isRemovablePure();
    }

    static boolean isPure(Expr expr) {
        return expr.isPure();
    }

    static boolean isRemovablePure(Instruction instruction) {
        return instruction instanceof Expr && instruction.isRemovablePure();
    }
}
