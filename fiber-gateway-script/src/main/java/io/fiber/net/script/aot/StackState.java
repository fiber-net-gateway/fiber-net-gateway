package io.fiber.net.script.aot;

import java.util.Arrays;

public final class StackState {
    private static final SsaValue[] EMPTY = new SsaValue[0];

    private SsaValue[] values;
    private int size;

    public StackState() {
        this.values = EMPTY;
    }

    private StackState(SsaValue[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public static StackState empty() {
        return new StackState();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public SsaValue get(int index) {
        if (index < 0 || index >= size) {
            throw new SsaException("stack index out of bounds: " + index + ", size=" + size);
        }
        return values[index];
    }

    public SsaValue peek() {
        return get(size - 1);
    }

    public void push(SsaValue value) {
        if (value == null) {
            throw new SsaException("cannot push null ssa value");
        }
        ensure(size + 1);
        values[size++] = value;
    }

    public SsaValue pop(int pc) {
        if (size <= 0) {
            throw new SsaException("operand stack underflow at pc " + pc);
        }
        SsaValue value = values[--size];
        values[size] = null;
        return value;
    }

    public void replaceTop(SsaValue value, int pc) {
        if (size <= 0) {
            throw new SsaException("operand stack underflow at pc " + pc);
        }
        values[size - 1] = value;
    }

    public StackState copy() {
        return new StackState(size == 0 ? EMPTY : Arrays.copyOf(values, size), size);
    }

    SsaValue[] snapshot() {
        return size == 0 ? EMPTY : Arrays.copyOf(values, size);
    }

    boolean replace(SsaValue oldValue, SsaValue newValue) {
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            if (values[i] == oldValue) {
                values[i] = newValue;
                changed = true;
            }
        }
        return changed;
    }

    private void ensure(int capacity) {
        if (values.length >= capacity) {
            return;
        }
        int next = values.length == 0 ? 4 : values.length << 1;
        while (next < capacity) {
            next <<= 1;
        }
        values = Arrays.copyOf(values, next);
    }
}
