package io.fiber.net.http.util;


import io.fiber.net.common.utils.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class IpUtils {

    public static InetAddress tryToInetAddress(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return null;
        }
        InetAddress address = tryToInet4Adr(ip);
        if (address != null) {
            return address;
        }

        return tryToInet6Adr(ip);
    }

    static InetAddress tryToInet6Adr(String ip) {
        if (ip.charAt(0) == '[') {
            decodeIpv6(ip, 1, ip.length() - 1);
        }
        return decodeIpv6(ip, 0, ip.length());
    }


    static Inet4Address tryToInet4Adr(String ip) {
        if (ip.length() > 15) {
            return null;
        }
        byte[] bs = new byte[4];
        int n = 0;
        int j = 0;
        char[] chars = ip.toCharArray();
        int length = chars.length;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= '0' && c <= '9') {
                if (n == 0 && i > 0) {
                    if (chars[i - 1] != '.') {
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
        if (j != 3 || chars[length - 1] == '.') {
            return null;
        }
        bs[3] = (byte) n;
        try {
            return (Inet4Address) InetAddress.getByAddress(ip, bs);
        } catch (Exception e) {
            return null;
        }
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

    static InetAddress decodeIpv6(String input, int pos, int limit) {
        byte[] address = new byte[16];
        int b = 0;
        int compress = -1;
        int groupOffset = -1;

        for (int i = pos; i < limit; ) {
            if (b == address.length) return null; // Too many groups.

            // Read a delimiter.
            if (i + 2 <= limit && input.regionMatches(i, "::", 0, 2)) {
                // Compression "::" delimiter, which is anywhere in the input, including its prefix.
                if (compress != -1) return null; // Multiple "::" delimiters.
                i += 2;
                b += 2;
                compress = b;
                if (i == limit) break;
            } else if (b != 0) {
                // Group separator ":" delimiter.
                if (input.regionMatches(i, ":", 0, 1)) {
                    i++;
                } else if (input.regionMatches(i, ".", 0, 1)) {
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
                char c = input.charAt(i);
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

        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }
}
