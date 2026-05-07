package io.fiber.net.script.aot;

import io.fiber.net.common.utils.Assert;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AotRuntimePlan {
    final int stackSize;
    final int varTableSize;
    final int[] codes;
    final Object[] operands;
    final long[] pos;
    final int[] expIns;

    public AotRuntimePlan(int stackSize,
                          int varTableSize,
                          int[] codes,
                          Object[] operands,
                          long[] pos,
                          int[] exceptionTable) {
        this.stackSize = Math.max(1, stackSize);
        this.varTableSize = Math.max(0, varTableSize);
        this.codes = codes == null ? new int[0] : codes.clone();
        this.operands = operands == null ? new Object[0] : operands.clone();
        this.pos = pos == null ? new long[this.codes.length] : pos.clone();
        this.expIns = computeExpIns(exceptionTable);
    }

    private static int[] computeExpIns(int[] exceptionTable) {
        if (exceptionTable == null || exceptionTable.length == 0) {
            return null;
        }
        TreeMap<Integer, Integer> ranges = new TreeMap<>();
        TreeSet<Integer> catches = new TreeSet<>();
        int lt = -1;
        for (int i = 0; i < exceptionTable.length; i += 3) {
            int tryBegin = exceptionTable[i];
            int catchBegin = exceptionTable[i + 1];
            int catchEnd = exceptionTable[i + 2];
            Assert.isTrue(tryBegin >= lt && tryBegin < catchBegin && catchBegin < catchEnd);
            ranges.put(tryBegin, catchBegin);
            catches.add(catchBegin);
            Integer latter = catches.ceiling(catchEnd);
            ranges.put(catchBegin, latter == null ? -1 : latter);
            lt = tryBegin;
        }
        int size = ranges.size();
        int[] expIns = new int[size * 2];
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : ranges.entrySet()) {
            expIns[i] = entry.getKey();
            expIns[i + size] = entry.getValue();
            i++;
        }
        return expIns;
    }
}
