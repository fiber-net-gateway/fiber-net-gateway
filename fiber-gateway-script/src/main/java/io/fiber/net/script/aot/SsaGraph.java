package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SsaGraph {
    private final Cfg cfg;
    private final List<SsaBlock> blocks;
    private final SsaBlock[] blockById;
    private int valueCount;
    private final int varCount;
    private final ArrayList<Object> constants;
    private final DominatorTree dominatorTree;
    private final LoopInfo loopInfo;

    SsaGraph(Cfg cfg, List<SsaBlock> blocks, int valueCount, int varCount) {
        this(cfg, blocks, valueCount, varCount, null);
    }

    SsaGraph(Cfg cfg, List<SsaBlock> blocks, int valueCount, int varCount, Object[] constants) {
        this.cfg = cfg;
        this.blocks = Collections.unmodifiableList(blocks);
        this.blockById = new SsaBlock[blocks.size()];
        for (SsaBlock block : blocks) {
            blockById[block.id()] = block;
        }
        this.valueCount = valueCount;
        this.varCount = varCount;
        this.constants = constants == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(constants));
        this.dominatorTree = new DominatorTree(cfg);
        this.loopInfo = new LoopInfo(cfg, dominatorTree);
    }

    public Cfg cfg() {
        return cfg;
    }

    public List<SsaBlock> blocks() {
        return blocks;
    }

    public SsaBlock entryBlock() {
        return blocks.get(0);
    }

    public SsaBlock blockFor(BasicBlock block) {
        if (block == null || block.id() < 0 || block.id() >= blockById.length) {
            return null;
        }
        return blockById[block.id()];
    }

    public int valueCount() {
        return valueCount;
    }

    public int varCount() {
        return varCount;
    }

    public Object constant(int index) {
        if (index < 0 || index >= constants.size()) {
            return null;
        }
        return constants.get(index);
    }

    public List<Object> constants() {
        return Collections.unmodifiableList(constants);
    }

    SsaValue createConst(SsaBlock block, JsonNode constant) {
        return createConstBefore(block, null, constant);
    }

    SsaValue createConstBefore(SsaBlock block, SsaValue before, JsonNode constant) {
        int index = constantIndex(constant);
        SsaValue value = new SsaValue(valueCount++, SsaKind.CONST, block, null, index, null);
        if (before == null) {
            block.addInstruction(value);
        } else {
            block.addInstructionBefore(before, value);
        }
        return value;
    }

    private int constantIndex(JsonNode constant) {
        for (int i = 0; i < constants.size(); i++) {
            Object item = constants.get(i);
            if (constant.equals(item)) {
                return i;
            }
        }
        constants.add(constant);
        return constants.size() - 1;
    }

    public DominatorTree dominatorTree() {
        return dominatorTree;
    }

    public LoopInfo loopInfo() {
        return loopInfo;
    }
}
