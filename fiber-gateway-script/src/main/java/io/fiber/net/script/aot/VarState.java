package io.fiber.net.script.aot;

import java.util.Arrays;

public final class VarState {
    private final SsaValue[] values;

    public VarState(int size) {
        this.values = new SsaValue[size];
    }

    private VarState(SsaValue[] values) {
        this.values = values;
    }

    public int size() {
        return values.length;
    }

    public SsaValue get(int index) {
        checkIndex(index);
        return values[index];
    }

    public void set(int index, SsaValue value) {
        checkIndex(index);
        if (value == null) {
            throw new SsaException("cannot set null var " + index);
        }
        values[index] = value;
    }

    public VarState copy() {
        return new VarState(Arrays.copyOf(values, values.length));
    }

    SsaValue[] snapshot() {
        return Arrays.copyOf(values, values.length);
    }

    boolean replace(SsaValue oldValue, SsaValue newValue) {
        boolean changed = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == oldValue) {
                values[i] = newValue;
                changed = true;
            }
        }
        return changed;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= values.length) {
            throw new SsaException("var index out of bounds: " + index + ", size=" + values.length);
        }
    }
}
