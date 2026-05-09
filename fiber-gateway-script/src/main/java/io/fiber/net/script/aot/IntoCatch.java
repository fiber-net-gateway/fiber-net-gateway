package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public class IntoCatch extends Instruction {
    final List<Block> tryBlocks = new ArrayList<>();

    protected IntoCatch(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    public void addTry(Block block) {
        tryBlocks.add(block);
    }

    public List<Block> getTryBlocks() {
        return tryBlocks;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
