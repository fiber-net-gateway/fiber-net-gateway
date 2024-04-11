package io.fiber.net.script.run;


import io.fiber.net.common.json.JsonNode;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    private static final sun.misc.Unsafe UNSAFE;
    private static final long JSON_NODE_OCCUPY;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            JSON_NODE_OCCUPY = (getObjectOffset(UnsafeJsonNodeObj.class.getDeclaredField("_m4"))
                    - getObjectOffset(UnsafeJsonNodeObj.class.getDeclaredField("_m0"))) / 4;
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            throw new IllegalStateException(e);
        }
    }

    public static Object getObject(Object thisObj, long offset) {
        return UNSAFE.getObject(thisObj, offset);
    }

    public static JsonNode getJsonNodeObject(Object thisObj, long offset) {
        return (JsonNode) UNSAFE.getObject(thisObj, offset);
    }

    public static long getObjectOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    public static long getJsonNodeOccupy() {
        return JSON_NODE_OCCUPY;
    }

    private static class UnsafeJsonNodeObj {
        private int _i;
        private JsonNode _m0, _m1, _m2, _m3, _m4;
        private int _i0, _i1;
    }


}

