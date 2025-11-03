/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.fiber.net.common.utils;

import io.netty.handler.codec.http.cookie.CookieHeaderNames;

import java.util.BitSet;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used server side.
 * <p>
 * Only name and value fields are expected, so old fields are not populated (path, domain, etc).
 * <p>
 * Old <a href="https://tools.ietf.org/html/rfc2965">RFC2965</a> cookies are still supported,
 * old fields will simply be ignored.
 */
public final class ServerCookieDecodeUtil {

    @FunctionalInterface
    public interface CookieAcceptor {
        boolean accept(String src, int nameBegin, int nameEnd, int valueBegin, int valueEnd, boolean wrap);
    }

    private static final String RFC2965_VERSION = "$Version";

    private static final String RFC2965_PATH = "$" + CookieHeaderNames.PATH;

    private static final String RFC2965_DOMAIN = "$" + CookieHeaderNames.DOMAIN;

    private static final String RFC2965_PORT = "$Port";


    public static boolean decode(String header, boolean strict, CookieAcceptor acceptor) {
        final int headerLen = checkNotNull(header, "header").length();

        if (headerLen == 0) {
            return false;
        }

        int i = 0;

        boolean rfc2965Style = false;
        if (header.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
            // RFC 2965 style cookie, move to after version value
            i = header.indexOf(';') + 1;
            rfc2965Style = true;
        }

        loop:
        for (; ; ) {

            // Skip spaces and separators.
            for (; ; ) {
                if (i == headerLen) {
                    break loop;
                }
                char c = header.charAt(i);
                if (c == '\t' || c == '\n' || c == 0x0b || c == '\f'
                        || c == '\r' || c == ' ' || c == ',' || c == ';') {
                    i++;
                    continue;
                }
                break;
            }

            int nameBegin = i;
            int nameEnd;
            int valueBegin;
            int valueEnd;

            for (; ; ) {

                char curChar = header.charAt(i);
                if (curChar == ';') {
                    // NAME; (no value till ';')
                    nameEnd = i;
                    valueBegin = valueEnd = -1;
                    break;

                } else if (curChar == '=') {
                    // NAME=VALUE
                    nameEnd = i;
                    i++;
                    if (i == headerLen) {
                        // NAME= (empty value, i.e. nothing after '=')
                        valueBegin = valueEnd = 0;
                        break;
                    }

                    valueBegin = i;
                    // NAME=VALUE;
                    int semiPos = header.indexOf(';', i);
                    valueEnd = i = semiPos > 0 ? semiPos : headerLen;
                    break;
                } else {
                    i++;
                }

                if (i == headerLen) {
                    // NAME (no value till the end of string)
                    nameEnd = headerLen;
                    valueBegin = valueEnd = -1;
                    break;
                }
            }

            if (rfc2965Style && (header.regionMatches(nameBegin, RFC2965_PATH, 0, RFC2965_PATH.length()) ||
                    header.regionMatches(nameBegin, RFC2965_DOMAIN, 0, RFC2965_DOMAIN.length()) ||
                    header.regionMatches(nameBegin, RFC2965_PORT, 0, RFC2965_PORT.length()))) {

                // skip obsolete RFC2965 fields
                continue;
            }

            if (nameBegin == nameEnd || valueBegin == -1) {
                continue;
            }

            boolean wrap = false;
            {
                if (valueEnd > valueBegin && header.charAt(valueBegin) == '"') {
                    if (valueEnd >= valueBegin + 2 && header.charAt(valueEnd - 1) == '"') {
                        wrap = true;
                        valueBegin++;
                        valueEnd--;
                    } else {
                        continue;
                    }
                }
            }

            if (strict && hasInvalidOctet(header, nameBegin, nameEnd, VALID_COOKIE_NAME_OCTETS)) {
                continue;
            }

            if (strict && hasInvalidOctet(header, valueBegin, valueEnd, VALID_COOKIE_VALUE_OCTETS)) {
                continue;
            }

            if (acceptor.accept(header, nameBegin, nameEnd, valueBegin, valueEnd, wrap)) {
                return true;
            }
        }
        return false;
    }


    static boolean hasInvalidOctet(String src, int start, int end, BitSet bits) {
        for (int i = start; i < end; i++) {
            char c = src.charAt(i);
            if (!bits.get(c)) {
                return true;
            }
        }
        return false;
    }

    private static final BitSet VALID_COOKIE_NAME_OCTETS = validCookieNameOctets();

    private static final BitSet VALID_COOKIE_VALUE_OCTETS = validCookieValueOctets();

    // token = 1*<any CHAR except CTLs or separators>
    // separators = "(" | ")" | "<" | ">" | "@"
    // | "," | ";" | ":" | "\" | <">
    // | "/" | "[" | "]" | "?" | "="
    // | "{" | "}" | SP | HT
    private static BitSet validCookieNameOctets() {
        BitSet bits = new BitSet();
        for (int i = 32; i < 127; i++) {
            bits.set(i);
        }
        int[] separators = new int[]
                {'(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}', ' ', '\t'};
        for (int separator : separators) {
            bits.set(separator, false);
        }
        return bits;
    }

    // cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
    // US-ASCII characters excluding CTLs, whitespace, DQUOTE, comma, semicolon, and backslash
    private static BitSet validCookieValueOctets() {
        BitSet bits = new BitSet();
        bits.set(0x21);
        for (int i = 0x23; i <= 0x2B; i++) {
            bits.set(i);
        }
        for (int i = 0x2D; i <= 0x3A; i++) {
            bits.set(i);
        }
        for (int i = 0x3C; i <= 0x5B; i++) {
            bits.set(i);
        }
        for (int i = 0x5D; i <= 0x7E; i++) {
            bits.set(i);
        }
        return bits;
    }
}
