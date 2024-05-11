package io.fiber.net.common.utils;


import java.nio.charset.StandardCharsets;

public class CharArrUtil {
    public static final char[] EMPTY = new char[0];
    public static final byte[] EMPTY_BYTES = Constant.EMPTY_BYTE_ARR;


    public static char[] toCharArr(String str) {
        return str.toCharArray();
    }

    public static byte[] toByteArr(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
