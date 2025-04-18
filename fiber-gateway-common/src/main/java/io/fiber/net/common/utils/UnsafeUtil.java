package io.fiber.net.common.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

@SuppressWarnings("deprecated")
public class UnsafeUtil {

    private UnsafeUtil() {
    }

    private static class Un {
        static final Unsafe UNSAFE;

        static {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                UNSAFE = (Unsafe) theUnsafe.get(null);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new RuntimeException(e);
            }
        }

        public static boolean hasUnsafe() {
            return UNSAFE != null;
        }
    }

    public static long fieldOffset(Field field) {
        return Un.UNSAFE.objectFieldOffset(field);
    }

    public static long getObjectLong(Object thisObj, long offset) {
        return Un.UNSAFE.getLong(thisObj, offset);
    }

    public static int getObjectInt(Object thisObj, long offset) {
        return Un.UNSAFE.getInt(thisObj, offset);
    }

    public static Object getObject(Object thisObj, long offset) {
        return Un.UNSAFE.getObject(thisObj, offset);
    }

    public static void setLong(Object thisObj, long offset, long x) {
        Un.UNSAFE.putLong(thisObj, offset, x);
    }

    public static void setInt(Object thisObj, long offset, int x) {
        Un.UNSAFE.putInt(thisObj, offset, x);
    }

    public static void setObject(Object thisObj, long offset, Object x) {
        Un.UNSAFE.putObject(thisObj, offset, x);
    }
}
