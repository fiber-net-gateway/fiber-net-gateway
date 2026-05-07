package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.Map;

public final class CompactBytecode {
    private final int[] codes;
    private final int[] exceptionTable;
    private final Map<Integer, Integer> oldPcToNewPc;
    private final Map<BasicBlock, Integer> blockStartPc;

    CompactBytecode(int[] codes,
                    int[] exceptionTable,
                    Map<Integer, Integer> oldPcToNewPc,
                    Map<BasicBlock, Integer> blockStartPc) {
        this.codes = codes;
        this.exceptionTable = exceptionTable;
        this.oldPcToNewPc = Collections.unmodifiableMap(oldPcToNewPc);
        this.blockStartPc = Collections.unmodifiableMap(blockStartPc);
    }

    public int[] codes() {
        return codes.clone();
    }

    public int[] exceptionTable() {
        return exceptionTable.clone();
    }

    public Map<Integer, Integer> oldPcToNewPc() {
        return oldPcToNewPc;
    }

    public Map<BasicBlock, Integer> blockStartPc() {
        return blockStartPc;
    }
}
