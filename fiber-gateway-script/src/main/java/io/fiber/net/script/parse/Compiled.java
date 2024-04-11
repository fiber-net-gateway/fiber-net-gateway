package io.fiber.net.script.parse;

import io.fiber.net.common.utils.Assert;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Compiled {
    int stackSize;
    int varTableSize;
    long[] pos;
    int[] codes;
    Object[] operands;
    int[] exceptionTable;
    int[] expIns;

    public Compiled(int stackSize,
                    int varTableSize,
                    long[] pos,
                    int[] codes,
                    Object[] operands) {
        this.stackSize = stackSize;
        this.varTableSize = varTableSize;
        this.pos = pos;
        this.codes = codes;
        this.operands = operands;
    }

    void init() {
        // [1,100, 200] [2,97,5],[1,2,97,]
        if (exceptionTable != null) {
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
                if (latter != null) {
                    /*
                     * try {
                     *  -> try {} catch() {}
                     * }catch(){
                     *
                     * }
                     */
                    ranges.put(catchBegin, latter);
                } else {
                    /*
                     * try {
                     * }catch(){
                     *  -> try {} catch() {}
                     * }
                     * // =====================
                     * try {
                     * }catch(){
                     * }
                     * -> try {} catch() {}
                     */
                    ranges.put(catchBegin, -1);
                }
                lt = tryBegin;
            }
            int size = ranges.size();
            expIns = new int[size * 2];
            int i = 0;
            for (Map.Entry<Integer, Integer> entry : ranges.entrySet()) {
                expIns[i] = entry.getKey();
                expIns[i + size] = entry.getValue();
                i++;
            }
        }
    }

    public int getStackSize() {
        return stackSize;
    }

    public int getVarTableSize() {
        return varTableSize;
    }

    public long[] getPos() {
        return pos;
    }

    public int[] getCodes() {
        return codes;
    }

    public Object[] getOperands() {
        return operands;
    }

    public int[] getExceptionTable() {
        return exceptionTable;
    }

    public int[] getExpIns() {
        return expIns;
    }
}