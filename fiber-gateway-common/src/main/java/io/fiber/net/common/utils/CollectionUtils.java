package io.fiber.net.common.utils;

import java.util.Collection;
import java.util.Map;

public class CollectionUtils {
    public static boolean isEmpty(Collection<?> col) {
        return col == null || col.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> col) {
        return col != null && !col.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> col) {
        return col == null || col.isEmpty();
    }

    public static boolean isNotEmpty(Map<?, ?> col) {
        return col != null && !col.isEmpty();
    }
}
