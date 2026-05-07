package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.List;

public final class Cfg {
    private final Bytecode[] bytecodes;
    private final List<BasicBlock> blocks;
    private final BasicBlock[] blockByPc;
    private final List<ExceptionRegion> exceptionRegions;

    Cfg(Bytecode[] bytecodes,
        List<BasicBlock> blocks,
        BasicBlock[] blockByPc,
        List<ExceptionRegion> exceptionRegions) {
        this.bytecodes = bytecodes;
        this.blocks = Collections.unmodifiableList(blocks);
        this.blockByPc = blockByPc;
        this.exceptionRegions = Collections.unmodifiableList(exceptionRegions);
    }

    public Bytecode[] bytecodes() {
        return bytecodes.clone();
    }

    public int codeLength() {
        return bytecodes.length;
    }

    public BasicBlock entryBlock() {
        return blocks.get(0);
    }

    public List<BasicBlock> blocks() {
        return blocks;
    }

    public BasicBlock blockAtPc(int pc) {
        if (pc < 0 || pc >= blockByPc.length) {
            return null;
        }
        return blockByPc[pc];
    }

    public List<ExceptionRegion> exceptionRegions() {
        return exceptionRegions;
    }
}
