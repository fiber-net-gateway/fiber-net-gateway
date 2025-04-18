package io.fiber.net.common.utils;

public class UDecoder {

    static class DecodeException extends RuntimeException {

        public DecodeException(String s) {
            super(s);
        }
    }

    public static final boolean ALLOW_ENCODED_SLASH = false;

    /**
     * Unexpected end of data.
     */
    private static final DecodeException EXCEPTION_EOF = new DecodeException("EOF");

    /**
     * %xx with not-hex digit
     */
    private static final DecodeException EXCEPTION_NOT_HEX_DIGIT = new DecodeException(
            "isHexDigit");

    /**
     * %-encoded slash is forbidden in resource path
     */
    private static final DecodeException EXCEPTION_SLASH = new DecodeException(
            "noSlash");

    public static String convert(String content, boolean query) {
        byte[] data = content.getBytes();
        int idx = 0;
        final boolean noSlash = !(ALLOW_ENCODED_SLASH || query);
        boolean modified = false;
        for (int i = 0; i < data.length; i++) {
            switch (data[i]) {
                case '/':
                    if (!query && idx > 0 && data[idx - 1] == '/') {
                        modified = true;
                        continue;
                    }
                    data[idx++] = data[i];
                    break;
                case '+':
                    if (query) {
                        data[idx++] = (byte) ' ';
                        modified = true;
                    } else {
                        data[idx++] = data[i];
                    }
                    break;
                case '%':
                    // read next 2 digits
                    if (i + 2 >= data.length) {
                        throw EXCEPTION_EOF;
                    }
                    byte b1 = data[i + 1];
                    byte b2 = data[i + 2];
                    if (!isHexDigit(b1) || !isHexDigit(b2)) {
                        throw EXCEPTION_NOT_HEX_DIGIT;
                    }

                    i += 2;
                    int res = x2c(b1, b2);
                    if (noSlash && (res == '/')) {
                        throw EXCEPTION_SLASH;
                    }
                    data[idx++] = (byte) res;
                    modified = true;
                    break;
                default:
                    data[idx++] = data[i];
                    break;
            }
        }
        return modified ? new String(data, 0, idx) : content ;
    }

    private static boolean isHexDigit(int c) {
        return ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'f') ||
                (c >= 'A' && c <= 'F'));
    }

    private static int x2c(byte b1, byte b2) {
        int digit = (b1 >= 'A') ? ((b1 & 0xDF) - 'A') + 10 :
                (b1 - '0');
        digit *= 16;
        digit += (b2 >= 'A') ? ((b2 & 0xDF) - 'A') + 10 :
                (b2 - '0');
        return digit;
    }

}
