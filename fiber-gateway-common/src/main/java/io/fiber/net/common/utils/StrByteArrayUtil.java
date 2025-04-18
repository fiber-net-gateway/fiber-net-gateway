package io.fiber.net.common.utils;

import io.netty.util.concurrent.FastThreadLocal;

/**
 * performance sensitive usage.
 */
@Deprecated
public class StrByteArrayUtil {
    public static final int CACHE_LEN = 2048;
    public static final FastThreadLocal<byte[]> LOCAL = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[CACHE_LEN];
        }
    };
}
