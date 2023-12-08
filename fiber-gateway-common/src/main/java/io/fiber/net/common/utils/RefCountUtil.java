package io.fiber.net.common.utils;

import io.netty.util.ReferenceCounted;

public class RefCountUtil {
    public static void release(ReferenceCounted ref) {
        if (ref.refCnt() > 0) {
            ref.release();
        }
    }

    public static void release(Object ref) {
        if (ref instanceof ReferenceCounted) {
            release((ReferenceCounted) ref);
        }
    }
}
