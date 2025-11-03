package io.fiber.net.common.utils;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class IpUtils {

    public static InetAddress tryToInetAddress(String ip) {
        byte[] bytes = parseIp(ip, 0, ip.length());
        if (bytes != null) {
            try {
                return InetAddress.getByAddress(ip, bytes);
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }


    static byte[] parseV4(String ip, int pos, int limit) {
        if (pos < 0 || limit > ip.length() || limit - pos <= 0 || limit - pos > 15) {
            return null;
        }
        byte[] bs = new byte[4];
        int n = 0;
        int j = 0;
        for (int i = pos; i < limit; i++) {
            char c = ip.charAt(i);
            if (c >= '0' && c <= '9') {
                if (n == 0 && i > pos) {
                    if (ip.charAt(i - 1) != '.') {
                        // 33.03. // error
                        return null;
                    }
                }

                n = n * 10 + (c - '0');
                if (n > 255) {
                    return null;
                }
            } else if (c == '.') {
                if (j >= 3) {
                    return null;
                }
                bs[j++] = (byte) n;
                n = 0;
            } else {
                return null;
            }
        }
        if (j != 3 || ip.charAt(limit - 1) == '.') {
            return null;
        }
        bs[3] = (byte) n;
        return bs;
    }

    static boolean decodeIpv4Suffix(
            String input, int pos, int limit, byte[] address, int addressOffset) {
        int b = addressOffset;

        for (int i = pos; i < limit; ) {
            if (b == address.length) return false; // Too many groups.

            // Read a delimiter.
            if (b != addressOffset) {
                if (input.charAt(i) != '.') return false; // Wrong delimiter.
                i++;
            }

            // Read 1 or more decimal digits for a value in 0..255.
            int value = 0;
            int groupOffset = i;
            for (; i < limit; i++) {
                char c = input.charAt(i);
                if (c < '0' || c > '9') break;
                if (value == 0 && groupOffset != i) return false; // Reject unnecessary leading '0's.
                value = (value * 10) + c - '0';
                if (value > 255) return false; // Value out of range.
            }
            int groupLength = i - groupOffset;
            if (groupLength == 0) return false; // No digits.

            // We've successfully read a byte.
            address[b++] = (byte) value;
        }

        return b == addressOffset + 4; // Too few groups. We wanted exactly four.
    }

    static int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    static byte[] parseIp(String input, int pos, int limit) {
        byte[] bytes = parseV4(input, pos, limit);
        if (bytes != null) {
            return bytes;
        }
        if (input.charAt(pos) == '[') {
            return parseV6(input, pos + 1, limit - 1);
        } else {
            return parseV6(input, pos, limit);
        }
    }

    private static byte[] parseV6(String input, int pos, int limit) {
        byte[] address = new byte[16];
        int b = 0;
        int compress = -1;
        int groupOffset = -1;

        for (int i = pos; i < limit; ) {
            if (b == address.length) return null; // Too many groups.

            // Read a delimiter.
            char c = input.charAt(i);
            if (i + 2 <= limit && c == ':' && input.charAt(i + 1) == ':') {
                // Compression "::" delimiter, which is anywhere in the input, including its prefix.
                if (compress != -1) return null; // Multiple "::" delimiters.
                i += 2;
                b += 2;
                compress = b;
                if (i == limit) break;
            } else if (b != 0) {
                // Group separator ":" delimiter.
                if (c == ':') {
                    i++;
                } else if (c == '.') {
                    // If we see a '.', rewind to the beginning of the previous group and parse as IPv4.
                    if (!decodeIpv4Suffix(input, groupOffset, limit, address, b - 2)) return null;
                    b += 2; // We rewound two bytes and then added four.
                    break;
                } else {
                    return null; // Wrong delimiter.
                }
            }

            // Read a group, one to four hex digits.
            int value = 0;
            groupOffset = i;
            for (; i < limit; i++) {
                int hexDigit = decodeHexDigit(c);
                if (hexDigit == -1) break;
                value = (value << 4) + hexDigit;
            }
            int groupLength = i - groupOffset;
            if (groupLength == 0 || groupLength > 4) return null; // Group is the wrong size.

            // We've successfully read a group. Assign its value to our byte array.
            address[b++] = (byte) ((value >>> 8) & 0xff);
            address[b++] = (byte) (value & 0xff);
        }

        // All done. If compression happened, we need to move bytes to the right place in the
        // address. Here's a sample:
        //
        //      input: "1111:2222:3333::7777:8888"
        //     before: { 11, 11, 22, 22, 33, 33, 00, 00, 77, 77, 88, 88, 00, 00, 00, 00  }
        //   compress: 6
        //          b: 10
        //      after: { 11, 11, 22, 22, 33, 33, 00, 00, 00, 00, 00, 00, 77, 77, 88, 88 }
        //
        if (b != address.length) {
            if (compress == -1) return null; // Address didn't have compression or enough groups.
            System.arraycopy(address, compress, address, address.length - (b - compress), b - compress);
            Arrays.fill(address, compress, compress + (address.length - b), (byte) 0);
        }
        return address;
    }
}
