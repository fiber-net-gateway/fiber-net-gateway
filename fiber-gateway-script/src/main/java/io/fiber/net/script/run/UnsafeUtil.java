package io.fiber.net.script.run;


import io.fiber.net.common.json.JsonNode;
import io.netty.util.internal.PlatformDependent;

import java.lang.reflect.Field;

public class UnsafeUtil {
    private static final long JSON_NODE_OCCUPY;

    static {
        try {
            JSON_NODE_OCCUPY = (PlatformDependent.objectFieldOffset(UnsafeJsonNodeObj.class.getDeclaredField("_m4"))
                    - PlatformDependent.objectFieldOffset(UnsafeJsonNodeObj.class.getDeclaredField("_m0"))) / 4;
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            throw new IllegalStateException(e);
        }
    }

    public static Object getObject(Object thisObj, long offset) {
        return PlatformDependent.getObject(thisObj, offset);
    }

    public static JsonNode getJsonNodeObject(Object thisObj, long offset) {
        return (JsonNode) PlatformDependent.getObject(thisObj, offset);
    }

    public static long getObjectOffset(Field field) {
        return PlatformDependent.objectFieldOffset(field);
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

