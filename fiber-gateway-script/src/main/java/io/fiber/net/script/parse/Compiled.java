package io.fiber.net.script.parse;

import java.util.Map;
import java.util.TreeMap;

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
        // [1,100, 3] [2,97,5],[1,2,97,]
        if (exceptionTable != null) {
            TreeMap<Integer, Integer> ranges = new TreeMap<>();
            int l = -1, le = 0;
            for (int i = 0; i < exceptionTable.length; i += 3) {
                int s = exceptionTable[i];
                int e = exceptionTable[i + 1];
                int c = exceptionTable[i + 2];
                if (s < l || l == s && e >= le) {
                    throw new IllegalStateException("invalid exception table");
                }
                Map.Entry<Integer, Integer> ceilingEntry = ranges.ceilingEntry(s);
                if (ceilingEntry == null) {
                    ranges.put(s, c);
                    ranges.put(e, -1);
                } else {
                    int oldValue = ceilingEntry.getValue();
                    ranges.put(s, c);
                    ranges.put(e, oldValue);
                    int next = e + 1;
                    while ((ceilingEntry = ranges.ceilingEntry(next)) != null) {
                        if (ceilingEntry.getValue() != oldValue) {
                            break;
                        }
                        next = ceilingEntry.getKey() + 1;
                        ranges.remove(ceilingEntry.getKey());
                    }
                }

                l = s;
                le = e;
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