package io.fiber.net.common.utils;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

@SuppressWarnings("deprecation")
public class Headers {

    private static final QuadraticProbingHashTable<CharSequence, Boolean> HOP_HEADERS = new QuadraticProbingHashTable<>(
            32,
            AsciiString.CASE_INSENSITIVE_HASHER
    );

    static {
        HOP_HEADERS.put(HttpHeaderNames.CONNECTION, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.CONTENT_LENGTH, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.PROXY_CONNECTION, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.KEEP_ALIVE, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.PROXY_AUTHENTICATE, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.PROXY_AUTHORIZATION, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.TE, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.TRAILER, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.TRANSFER_ENCODING, Boolean.TRUE);
        HOP_HEADERS.put(HttpHeaderNames.UPGRADE, Boolean.TRUE);
    }

    public static boolean isHopHeaders(CharSequence key) {
        if (key == null) {
            return false;
        }
        return HOP_HEADERS.get(key) != null;
    }

}
