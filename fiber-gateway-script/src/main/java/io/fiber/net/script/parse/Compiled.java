package io.fiber.net.script.parse;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.run.Code;

import java.util.Arrays;
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
    String fileName;
    int[] lineStartOffsets;

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

    public String getFileName() {
        return fileName;
    }

    public void setSourceInfo(String fileName, int[] lineStartOffsets) {
        this.fileName = fileName;
        this.lineStartOffsets = lineStartOffsets;
    }

    public int getLineByOffset(int offset) {
        int[] offsets = lineStartOffsets;
        if (offsets == null || offsets.length == 0) {
            return -1;
        }
        int idx = Arrays.binarySearch(offsets, offset);
        if (idx < 0) {
            idx = -idx - 2;
        }
        return idx < 0 ? 1 : idx + 1;
    }

    public static int[] computeLineStartOffsets(String script) {
        int max = script.length();
        int[] offsets = new int[16];
        int len = 1;
        offsets[0] = 0;
        for (int i = 0; i < max; i++) {
            char ch = script.charAt(i);
            int next;
            if (ch == '\n' || ch == '\u2028' || ch == '\u2029') {
                next = i + 1;
            } else if (ch == '\r') {
                if (i + 1 < max && script.charAt(i + 1) == '\n') {
                    next = i + 2;
                    i++;
                } else {
                    next = i + 1;
                }
            } else {
                continue;
            }
            if (next < max) {
                if (offsets.length <= len) {
                    offsets = Arrays.copyOf(offsets, offsets.length << 1);
                }
                offsets[len++] = next;
            }
        }
        return Arrays.copyOf(offsets, len);
    }

    public boolean containsAsyncIS() {
        for (int code : codes) {
            switch (code & 0xFF) {
                case Code.CALL_ASYNC_CONST:
                case Code.CALL_ASYNC_FUNC:
                case Code.CALL_ASYNC_FUNC_SPREAD:
                    return true;
            }
        }
        return false;
    }
}
