package io.fiber.net.common.utils;

public class ArrayUtils {
    public static <T> boolean isEmpty(char[] arr) {
        return arr == null || arr.length == 0;
    }

    public static <T> boolean isEmpty(T[] arr) {
        return arr == null || arr.length == 0;
    }

    public static <T> boolean isNotEmpty(T[] arr) {
        return arr != null && arr.length != 0;
    }

    public static <T> boolean contains(T[] arr, T search) {
        if (arr == null) {
            return false;
        }
        for (T t : arr) {
            if (t == search) {
                return true;
            }
            if (search != null && search.equals(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNotEmpty(byte[] body) {
        return body != null && body.length > 0;
    }
}
