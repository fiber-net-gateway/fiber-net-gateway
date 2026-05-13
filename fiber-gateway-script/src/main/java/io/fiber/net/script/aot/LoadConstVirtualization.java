package io.fiber.net.script.aot;

public class LoadConstVirtualization {

    private final Cfg cfg;

    public LoadConstVirtualization(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (!(instruction instanceof LoadConst)) {
                    continue;
                }
                block.removeInstruction(instruction);
                changed = true;
            }
        }
        return changed;
    }
}
