package io.fiber.net.common.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.concurrent.FastThreadLocal;

@SuppressWarnings("deprecation")
public class UriCodec {


    private UriCodec() {
    }


    private static final int CACHE_LEN = StrByteArrayUtil.CACHE_LEN;
    private static final FastThreadLocal<byte[]> LOCAL = StrByteArrayUtil.LOCAL;

// 从 nginx 抄了代码


    private static final int sw_usual = 0, sw_slash = 1, sw_dot = 2, sw_dot_dot = 3, sw_quoted = 4, sw_quoted_second = 5;
    private static final int[] usual = {0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0x7fff37d6, /* 0111 1111 1111 1111  0011 0111 1101 0110 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0x7fffffff, /* 0111 1111 1111 1111  1111 1111 1111 1111 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};


    private static int findQueryEndIndex(int p, int uri_end, byte[] uri_data) {
        while (p < uri_end) {
            if (uri_data[p++] != '#') {
                continue;
            }
            return p - 1;
        }
        return p;
    }

    public interface Callback {
        void accept(String path, int argsStart, int argsEnd);
    }

    @SuppressWarnings("deprecation")
    public static void parseComplexUri(String uri, Callback callback) {
        if (StringUtils.isEmpty(uri)) {
            callback.accept("/", 0, 0);
            return;
        }

        int length = uri.length();
        if (length + 1 <= CACHE_LEN) {
            byte[] array = LOCAL.get();
            uri.getBytes(0, length, array, 0);
            array[length] = 0;
            ngx_http_parse_complex_uri(array, 0, uri, callback);
        } else {
            ByteBuf buf = ByteBufAllocator.DEFAULT.heapBuffer(length);
            byte[] array = buf.array();
            int off = buf.arrayOffset();
            uri.getBytes(0, length, array, off);
            array[length] = 0;
            try {
                ngx_http_parse_complex_uri(array, off, uri, callback);
            } finally {
                buf.release();
            }
        }
    }

    public static void ngx_http_parse_complex_uri(byte[] data, int off, String uri, Callback uriCallback) {
        final byte[] uri_data = data;

        byte c, ch, decoded = 0;
        int p, u, uri_end = uri.length();
        int state, quoted_state = sw_usual;

        int[] usual = UriCodec.usual;

        state = sw_usual;
        p = 0;
        u = off;
        ch = uri_data[p++];

        while (p <= uri_end) {

            /*
             * we use "ch = uri_data[p++]" inside the cycle, but this operation is safe,
             * because after the URI there is always at least one character:
             * the line feed
             */

            switch (state) {

                case sw_usual:

                    if ((usual[(ch & 0xFF) >>> 5] & (1 << (ch & 0x1f))) != 0) {
                        data[u++] = ch;
                        ch = uri_data[p++];
                        break;
                    }

                    switch (ch) {
                        case '/':
                            state = sw_slash;
                            data[u++] = ch;
                            break;
                        case '%':
                            quoted_state = state;
                            state = sw_quoted;
                            break;
                        case '?':
                            uriCallback.accept(new String(data, off, u - off), p, findQueryEndIndex(p, uri_end, uri_data));
                            return;
                        case '#':
                            uriCallback.accept(new String(data, off, u - off), 0, 0);
                            return;
                        case '.':
                            data[u++] = ch;
                            break;
                        case '+':
                            /* fall through */
                        default:
                            data[u++] = ch;
                            break;
                    }

                    ch = uri_data[p++];
                    break;

                case sw_slash:

                    if ((usual[(ch & 0xFF) >>> 5] & (1 << (ch & 0x1f))) != 0) {
                        state = sw_usual;
                        data[u++] = ch;
                        ch = uri_data[p++];
                        break;
                    }

                    switch (ch) {
                        case '/':
                            break;
                        case '.':
                            state = sw_dot;
                            data[u++] = ch;
                            break;
                        case '%':
                            quoted_state = state;
                            state = sw_quoted;
                            break;
                        case '?':
                            uriCallback.accept(new String(data, off, u - off), p, findQueryEndIndex(p, uri_end, uri_data));
                            return;
                        case '#':
                            uriCallback.accept(new String(data, off, u - off), 0, 0);
                            return;
                        case '+':
                            /* fall through */
                        default:
                            state = sw_usual;
                            data[u++] = ch;
                            break;
                    }

                    ch = uri_data[p++];
                    break;

                case sw_dot:

                    if ((usual[(ch & 0xFF) >>> 5] & (1 << (ch & 0x1f))) != 0) {
                        state = sw_usual;
                        data[u++] = ch;
                        ch = uri_data[p++];
                        break;
                    }

                    switch (ch) {
                        case '/':
                            state = sw_slash;
                            u--;
                            break;
                        case '.':
                            state = sw_dot_dot;
                            data[u++] = ch;
                            break;
                        case '%':
                            quoted_state = state;
                            state = sw_quoted;
                            break;
                        case '?':
                            uriCallback.accept(new String(data, off, u - 1 - off), p, findQueryEndIndex(p, uri_end, uri_data));
                            return;
                        case '#':
                            uriCallback.accept(new String(data, off, u - 1 - off), 0, 0);
                            return;
                        case '+':
                        default:
                            state = sw_usual;
                            data[u++] = ch;
                            break;
                    }

                    ch = uri_data[p++];
                    break;

                case sw_dot_dot:

                    if ((usual[(ch & 0xFF) >>> 5] & (1 << (ch & 0x1f))) != 0) {
                        state = sw_usual;
                        data[u++] = ch;
                        ch = uri_data[p++];
                        break;
                    }

                    switch (ch) {
                        case '/':
                        case '?':
                        case '#':
                            u -= 4;
                            for (; ; ) {
                                if (u < 0) {
                                    uriCallback.accept(null, 0, 0);
                                    return;
                                }
                                if (data[u] == '/') {
                                    u++;
                                    break;
                                }
                                u--;
                            }
                            if (ch == '?') {
                                uriCallback.accept(new String(data, off, u - off), p, findQueryEndIndex(p, uri_end, uri_data));
                                return;
                            }
                            if (ch == '#') {
                                uriCallback.accept(new String(data, off, u - off), 0, 0);
                                return;
                            }
                            state = sw_slash;
                            break;
                        case '%':
                            quoted_state = state;
                            state = sw_quoted;
                            break;
                        case '+':
                            /* fall through */
                        default:
                            state = sw_usual;
                            data[u++] = ch;
                            break;
                    }

                    ch = uri_data[p++];
                    break;

                case sw_quoted:

                    if (ch >= '0' && ch <= '9') {
                        decoded = (byte) (ch - '0');
                        state = sw_quoted_second;
                        ch = uri_data[p++];
                        break;
                    }

                    c = (byte) (ch | 0x20);
                    if (c >= 'a' && c <= 'f') {
                        decoded = (byte) (c - 'a' + 10);
                        state = sw_quoted_second;
                        ch = uri_data[p++];
                        break;
                    }

                    uriCallback.accept(null, 0, 0);
                    return;

                case sw_quoted_second:
                    if (ch >= '0' && ch <= '9') {
                        ch = (byte) ((decoded << 4) + (ch - '0'));

                        if (ch == '%' || ch == '#') {
                            state = sw_usual;
                            data[u++] = ch;
                            ch = uri_data[p++];
                            break;

                        } else if (ch == '\0') {
                            uriCallback.accept(null, 0, 0);
                            return;
                        }

                        state = quoted_state;
                        break;
                    }

                    c = (byte) (ch | 0x20);
                    if (c >= 'a' && c <= 'f') {
                        ch = (byte) ((decoded << 4) + (c - 'a') + 10);

                        if (ch == '?') {
                            state = sw_usual;
                            data[u++] = ch;
                            ch = uri_data[p++];
                            break;
                        }

                        state = quoted_state;
                        break;
                    }

                    uriCallback.accept(null, 0, 0);
                    return;
            }
        }

        if (state == sw_quoted || state == sw_quoted_second) {
            uriCallback.accept(null, 0, 0);
            return;
        }

        if (state == sw_dot) {
            u--;

        } else if (state == sw_dot_dot) {
            u -= 4;

            for (; ; ) {
                if (u < off) {
                    uriCallback.accept(null, 0, 0);
                    return;
                }

                if (data[u] == '/') {
                    u++;
                    break;
                }

                u--;
            }
        }
        uriCallback.accept(u + 1 != p ? new String(data, off, u - off) : uri, 0, 0);
    }
    /*
     * Per RFC 3986 only the following chars are allowed in URIs unescaped:
     *
     * unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
     * gen-delims    = ":" / "/" / "?" / "#" / "[" / "]" / "@"
     * sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
     *               / "*" / "+" / "," / ";" / "="
     *
     * And "%" can appear as a part of escaping itself.  The following
     * characters are not allowed and need to be escaped: %00-%1F, %7F-%FF,
     * " ", """, "<", ">", "\", "^", "`", "{", "|", "}".
     */

    /* " ", "#", "%", "?", not allowed */
    private static final int[] uri = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0xd000002d, /* 1101 0000 0000 0000  0000 0000 0010 1101 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x50000000, /* 0101 0000 0000 0000  0000 0000 0000 0000 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0xb8000001, /* 1011 1000 0000 0000  0000 0000 0000 0001 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};

    /* " ", "#", "%", "&", "+", ";", "?", not allowed */

    private static final int[] args = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0xd800086d, /* 1101 1000 0000 0000  0000 1000 0110 1101 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x50000000, /* 0101 0000 0000 0000  0000 0000 0000 0000 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0xb8000001, /* 1011 1000 0000 0000  0000 0000 0000 0001 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};

    /* not ALPHA, DIGIT, "-", ".", "_", "~" */

    private static final int[] uri_component = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0xfc009fff, /* 1111 1100 0000 0000  1001 1111 1111 1111 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x78000001, /* 0111 1000 0000 0000  0000 0000 0000 0001 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0xb8000001, /* 1011 1000 0000 0000  0000 0000 0000 0001 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};

    /* " ", "#", """, "%", "'", not allowed */

    private static final int[] html = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0x500000ad, /* 0101 0000 0000 0000  0000 0000 1010 1101 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x50000000, /* 0101 0000 0000 0000  0000 0000 0000 0000 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0xb8000001, /* 1011 1000 0000 0000  0000 0000 0000 0001 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};

    /* " ", """, "'", not allowed */

    private static final int[] refresh = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0x50000085, /* 0101 0000 0000 0000  0000 0000 1000 0101 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x50000000, /* 0101 0000 0000 0000  0000 0000 0000 0000 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0xd8000001, /* 1011 1000 0000 0000  0000 0000 0000 0001 */

            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
            0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */};

    /* " ", "%", %00-%1F */

    private static final int[] memcached = {0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */

            /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
            0x00000021, /* 0000 0000 0000 0000  0000 0000 0010 0001 */

            /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */

            /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */

            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */
            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */
            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */
            0x00000000, /* 0000 0000 0000 0000  0000 0000 0000 0000 */};
    private static final byte[] hex = "0123456789ABCDEF".getBytes();

    public static String escapeUri(String src) {
        return ngx_escape_uri(src, uri);
    }

    public static String escapeUriComponent(String src) {
        return ngx_escape_uri(src, uri_component);
    }

    public static String escapeArgs(String src) {
        return ngx_escape_uri(src, args);
    }

    public static String escapeHtml(String src) {
        return ngx_escape_uri(src, html);
    }

    public static String escapeRefresh(String src) {
        return ngx_escape_uri(src, refresh);
    }

    public static String escapeMemcached(String src) {
        return ngx_escape_uri(src, memcached);
    }

    @SuppressWarnings("deprecation")
    private static String ngx_escape_uri(String srcTxt, int[] escape) {
        int e = 0, size = srcTxt.length();
        byte[] src, dst;
        ByteBuf buf = null;
        int off;
        try {
            if (CharArrUtil.isUnsafeBytes()) {
                src = CharArrUtil.toReadOnlyAsciiCharArr(srcTxt);
                off = 0;
                dst = null;
            } else if ((size << 2) <= CACHE_LEN) {
                srcTxt.getBytes(0, size, dst = src = LOCAL.get(), off = 0);
            } else {
                buf = ByteBufAllocator.DEFAULT.heapBuffer(size << 2);
                srcTxt.getBytes(0, size, dst = src = buf.array(), off = buf.arrayOffset());
            }
            for (int i = 0; i < size; i++) {
                byte c = src[i + off];
                if ((escape[c >>> 5] & (1 << (c & 0x1f))) != 0) {
                    e++;
                }
            }
            if (e == 0) {
                return srcTxt;// no escape
            }

            int dstOff;
            if (dst != null) {
                dstOff = off + size;
            } else {
                int dstSize = size + (e << 1);
                if (dstSize <= CACHE_LEN) {
                    dst = LOCAL.get();
                    dstOff = 0;
                } else {
                    buf = ByteBufAllocator.DEFAULT.heapBuffer(dstSize);
                    dst = buf.array();
                    dstOff = buf.arrayOffset();
                }
            }

            int di = dstOff;
            for (int i = 0; i < size; i++) {
                byte c = src[i + off];
                if ((escape[c >>> 5] & (1 << (c & 0x1f))) != 0) {
                    dst[di++] = '%';
                    dst[di++] = hex[c >>> 4];
                    dst[di++] = hex[c & 0xf];
                } else {
                    dst[di++] = c;
                }
            }
            return new String(dst, dstOff, di - dstOff);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
}
