package io.fiber.net.common.utils;


import io.netty.util.internal.PlatformDependent;

import java.nio.charset.StandardCharsets;

public class CharArrUtil {

    private static class Unsafe {
        static final long ASCII_OFT;
        static final long BYTE_OFT;

        static {
            long af = -1, bf = -1;
            try {
                af = UnsafeUtil.fieldOffset(String.class.getDeclaredField("coder"));
                bf = PlatformDependent.objectFieldOffset(String.class.getDeclaredField("value"));
            } catch (NoSuchFieldException ignore) {
            }
            ASCII_OFT = af;
            BYTE_OFT = bf;
        }

        static byte[] unsafeAsciiCharArr(String str) {
            if (ASCII_OFT != -1 && PlatformDependent.getInt(str, ASCII_OFT) == 0) {
                return (byte[]) PlatformDependent.getObject(str, BYTE_OFT);
            }
            return str.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final boolean UNSAFE_BYTES =
            PlatformDependent.hasUnsafe() &&
                    PlatformDependent.javaVersion() >= 8
                    && Unsafe.BYTE_OFT != -1L;


    public static final char[] EMPTY = new char[0];
    public static final byte[] EMPTY_BYTES = Constant.EMPTY_BYTE_ARR;


    public static char[] toCharArr(String str) {
        return str.toCharArray();
    }

    public static byte[] toReadOnlyAsciiCharArr(String str) {
        if (UNSAFE_BYTES) {
            return Unsafe.unsafeAsciiCharArr(str);
        }
        return str.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static boolean isUnsafeBytes() {
        return UNSAFE_BYTES;
    }
}
