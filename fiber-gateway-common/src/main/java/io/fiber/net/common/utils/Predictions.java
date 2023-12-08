package io.fiber.net.common.utils;

public class Predictions {

    public static void notNull(Object obj, String errMsg) {
        if (obj == null) {
            throw new IllegalArgumentException(errMsg);
        }
    }

    public static <T> void arrayLen(T[] arr, int len, String errMsg) {
        if (arr == null || arr.length != len) {
            throw new IllegalArgumentException(errMsg + ": arr length not equal " + len);
        }
    }

    public static void strElementNotEmpty(String[] arr, String errMsg) {
        if (ArrayUtils.isEmpty(arr)) {
            throw new IllegalArgumentException(errMsg + ": arr is empty");
        }

        for (int i = 0; i < arr.length; i++) {
            if (StringUtils.isEmpty(arr[i])) {
                throw new IllegalArgumentException(String.format(
                        "%s :text array element of index %d is empty",
                        errMsg, i));
            }
        }
    }

    public static void textNotEmpty(String text, String errMsg) {
        if (StringUtils.isEmpty(text)) {
            throw new IllegalArgumentException(errMsg);
        }
    }
    public static String assertTextNotEmpty(String text, String errMsg) {
        if (StringUtils.isEmpty(text)) {
            throw new IllegalArgumentException(errMsg);
        }
        return text;
    }

    public static void assertTrue(boolean condition, String errMsg) {
        if (!condition) {
            throw new IllegalStateException(errMsg);
        }
    }

}
