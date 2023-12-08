package io.fiber.net.common.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class CharArrUtil {
    public static final char[] EMPTY = new char[0];

    private static class UnsafeUtil0 {
        static final Unsafe UNSAFE;
        static final long STR_CHARS_OFFSET;

        static {
            Unsafe unsafe;
            long off;
            try {
                Field valueField = String.class.getDeclaredField("value");
                // jdk 9 开始，string 底层是byte数组了。
                if (valueField.getType() == char[].class) {
                    Field field = Unsafe.class.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    unsafe = (Unsafe) field.get(null);
                    off = unsafe.objectFieldOffset(valueField);
                } else {
                    off = 0;
                    unsafe = null;
                }
            } catch (Throwable e) {
                off = 0;
                unsafe = null;
            }
            UNSAFE = unsafe;
            STR_CHARS_OFFSET = off;
        }

        static char[] toChar(String str) {
            return (char[]) UNSAFE.getObject(str, STR_CHARS_OFFSET);
        }
    }

    static final boolean hasUnsafe;

    static {
        boolean unsafe = false;
        try {
            unsafe = UnsafeUtil0.UNSAFE != null;
        } catch (Throwable ignore) {
        }
        hasUnsafe = unsafe;
    }

    public static char[] toCharArr(String str) {
        if (hasUnsafe) {
            return UnsafeUtil0.toChar(str);
        }
        return str.toCharArray();
    }
}
