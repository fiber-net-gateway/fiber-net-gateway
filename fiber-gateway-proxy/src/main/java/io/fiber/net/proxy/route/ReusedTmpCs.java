package io.fiber.net.proxy.route;

import java.util.Arrays;

public class ReusedTmpCs implements CharSequence {
    protected byte[] arr;
    protected int off;
    protected int len;

    public ReusedTmpCs() {
    }

    public ReusedTmpCs(byte[] arr, int len) {
        this.arr = arr;
        this.len = len;
    }

    public ReusedTmpCs(byte[] arr, int off, int len) {
        this.arr = arr;
        this.off = off;
        this.len = len;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public char charAt(int index) {
        return (char) arr[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new ReusedTmpCs(arr, off + start, (end - start));
    }

    @Override
    public String toString() {
        return new String(arr, off, len);
    }

    @SuppressWarnings("deprecation")
    public CharSequence allocTmpCs(String name, int start, int end) {
        int len = end - start;
        byte[] arr = this.arr;
        if (arr == null) {
            arr = this.arr = new byte[64];
        } else if (arr.length < len) {
            arr = this.arr = Arrays.copyOf(arr, arr.length << 1);
        }

        name.getBytes(start, end, arr, 0);
        this.len = len;
        return this;
    }
}
