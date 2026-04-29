package io.fiber.net.script;

public final class FunctionCallArgs {
    private static final int NO_SPREAD = -1;

    private final int count;
    private final int firstSpreadIndex;

    private FunctionCallArgs(int count, int firstSpreadIndex) {
        if (count < 0) {
            throw new IllegalArgumentException("argument count must be >= 0");
        }
        if (firstSpreadIndex >= count) {
            throw new IllegalArgumentException("spread index out of argument range");
        }
        this.count = count;
        this.firstSpreadIndex = firstSpreadIndex;
    }

    public static FunctionCallArgs of(int count) {
        return new FunctionCallArgs(count, NO_SPREAD);
    }

    public static FunctionCallArgs of(int count, int firstSpreadIndex) {
        return new FunctionCallArgs(count, firstSpreadIndex);
    }

    public int getCount() {
        return count;
    }

    public boolean hasSpread() {
        return firstSpreadIndex >= 0;
    }

    public int getFirstSpreadIndex() {
        return firstSpreadIndex;
    }
}
