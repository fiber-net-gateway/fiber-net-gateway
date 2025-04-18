//
//  ========================================================================
//  Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package io.fiber.net.proxy.util;


import io.netty.buffer.ByteBuf;

import java.io.CharArrayWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.function.BiConsumer;


/**
 * Handles coding of MIME  "x-www-form-urlencoded".
 * <p>
 * This class handles the encoding and decoding for either
 * the query string of a URL or the _content of a POST HTTP request.
 * </p>
 * <b>Notes</b>
 * <p>
 * The UTF-8 charset is assumed, unless otherwise defined by either
 * passing a parameter or setting the "org.eclipse.jetty.util.UrlEncoding.charset"
 * System property.
 * </p>
 * <p>
 * The hashtable either contains String single values, vectors
 * of String or arrays of Strings.
 * </p>
 * <p>
 * This class is only partially synchronised.  In particular, simple
 * get operations are not protected from concurrent updates.
 * </p>
 *
 * @see java.net.URLEncoder
 */
public class UrlEncoded extends MultiMap<String> implements Cloneable {

    public static final Charset ENCODING;
    static BitSet dontNeedEncoding;
    static final int caseDiff = ('a' - 'A');

    static {
        dontNeedEncoding = new BitSet(256);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            dontNeedEncoding.set(i);
        }
        dontNeedEncoding.set(' '); /* encoding a space to a + is done
         * in the encode() method */
        dontNeedEncoding.set('-');
        dontNeedEncoding.set('_');
        dontNeedEncoding.set('.');
        dontNeedEncoding.set('*');

        Charset encoding;
        try {
            String charset = System.getProperty("io.fiber.net.gateway.http.util.UrlEncoded.charset");
            encoding = charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
        } catch (Exception e) {
            encoding = StandardCharsets.UTF_8;
        }
        ENCODING = encoding;
    }

    public UrlEncoded(UrlEncoded url) {
        super(url);
    }

    public static void decodeUtf8To(String query, MultiMap<String> map) {
        decodeUtf8To(query, 0, query.length(), map::add);
    }

    /**
     * Decoded parameters to Map.
     *
     * @param query    the string containing the encoded parameters
     * @param offset   the offset within raw to decode from
     * @param length   the length of the section to decode
     * @param acceptor the {@link BiConsumer} to populate
     */
    public static void decodeUtf8To(String query, int offset, int length, BiConsumer<String, String> acceptor) {
        Utf8StringBuilder buffer = new Utf8StringBuilder();
        String key = null;
        String value;

        int end = offset + length;
        for (int i = offset; i < end; i++) {
            char c = query.charAt(i);
            switch (c) {
                case '&':
                    value = buffer.toReplacedString();
                    buffer.reset();
                    if (key != null) {
                        acceptor.accept(key, value);
                    } else if (value != null && !value.isEmpty()) {
                        acceptor.accept(value, "");
                    }
                    key = null;
                    break;

                case '=':
                    if (key != null) {
                        buffer.append(c);
                        break;
                    }
                    key = buffer.toReplacedString();
                    buffer.reset();
                    break;

                case '+':
                    buffer.append((byte) ' ');
                    break;

                case '%':
                    if (i + 2 < end) {
                        char hi = query.charAt(++i);
                        char lo = query.charAt(++i);
                        buffer.append(decodeHexByte(hi, lo));
                    } else {
                        throw new Utf8Appendable.NotUtf8Exception("Incomplete % encoding");
                    }
                    break;

                default:
                    buffer.append(c);
                    break;
            }
        }

        if (key != null) {
            value = buffer.toReplacedString();
            buffer.reset();
            acceptor.accept(key, value);
        } else if (buffer.length() > 0) {
            acceptor.accept(buffer.toReplacedString(), "");
        }
    }

    private static byte decodeHexByte(char hi, char lo) {
        try {
            return (byte) ((convertHexDigit(hi) << 4) + convertHexDigit(lo));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not valid encoding '%" + hi + lo + "'");
        }
    }

    /**
     * @param c An ASCII encoded character 0-9 a-f A-F
     * @return The byte value of the character 0-16.
     */
    public static int convertHexDigit(char c) {
        int d = ((c & 0x1f) + ((c >> 6) * 0x19) - 0x10);
        if (d < 0 || d > 15)
            throw new NumberFormatException("!hex " + c);
        return d;
    }

    /**
     *
     */
    @Override
    public Object clone() {
        return new UrlEncoded(this);
    }

    public static String decode(String s) {
        return decode(s, ENCODING);
    }


    public static String decode(String s, Charset charset) {

        boolean needToChange = false;
        int numChars = s.length();
        StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;

        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = s.charAt(i);
            switch (c) {
                case '+':
                    sb.append(' ');
                    i++;
                    needToChange = true;
                    break;
                case '%':
                    try {
                        // (numChars-i)/3 is an upper bound for the number
                        // of remaining bytes
                        if (bytes == null)
                            bytes = new byte[(numChars - i) / 3];
                        int pos = 0;

                        while (((i + 2) < numChars) &&
                                (c == '%')) {
                            int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                            if (v < 0)
                                throw new IllegalArgumentException("UrlEncoded: Illegal hex characters in escape (%) pattern - negative value");
                            bytes[pos++] = (byte) v;
                            i += 3;
                            if (i < numChars)
                                c = s.charAt(i);
                        }

                        if ((i < numChars) && (c == '%'))
                            throw new IllegalArgumentException(
                                    "UrlEncoded: Incomplete trailing escape (%) pattern");

                        sb.append(new String(bytes, 0, pos, charset));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "UrlEncoded: Illegal hex characters in escape (%) pattern - "
                                        + e.getMessage());
                    }
                    needToChange = true;
                    break;
                default:
                    sb.append(c);
                    i++;
                    break;
            }
        }

        return (needToChange ? sb.toString() : s);
    }

    public static String encode(String s) {
        return encode(s, ENCODING);
    }

    public static String encode(String s, Charset charset) {
        StringBuilder out = new StringBuilder(s.length());
        if (encodeInto0(s, charset, out)) {
            return out.toString();
        }
        return s;
    }

    public static void encodeInto(String s, Charset charset, StringBuilder out) {
        encodeInto0(s, charset, out);
    }

    private static boolean encodeInto0(String s, Charset charset, StringBuilder out) {
        CharArrayWriter charArrayWriter = new CharArrayWriter();

        boolean needToChange = false;
        for (int i = 0; i < s.length(); ) {
            int c = s.charAt(i);
            //System.out.println("Examining character: " + c);
            if (dontNeedEncoding.get(c)) {
                if (c == ' ') {
                    c = '+';
                    needToChange = true;
                }
                //System.out.println("Storing: " + c);
                out.append((char) c);
                i++;
            } else {
                do {
                    charArrayWriter.write(c);
                    if (c >= 0xD800 && c <= 0xDBFF) {
                        if ((i + 1) < s.length()) {
                            int d = s.charAt(i + 1);
                            if (d >= 0xDC00 && d <= 0xDFFF) {
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                } while (i < s.length() && !dontNeedEncoding.get((c = s.charAt(i))));

                charArrayWriter.flush();
                String str = new String(charArrayWriter.toCharArray());
                byte[] ba = str.getBytes(charset);
                for (byte b : ba) {
                    out.append('%');
                    char ch = Character.forDigit((b >> 4) & 0xF, 16);
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.append(ch);
                    ch = Character.forDigit(b & 0xF, 16);
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.append(ch);
                }
                charArrayWriter.reset();
                needToChange = true;
            }
        }

        return needToChange;
    }

    public static void encodeInto(String s, Charset charset, ByteBuf out) {
        CharArrayWriter charArrayWriter = new CharArrayWriter();
        for (int i = 0; i < s.length(); ) {
            int c = s.charAt(i);
            //System.out.println("Examining character: " + c);
            if (dontNeedEncoding.get(c)) {
                if (c == ' ') {
                    c = '+';
                }
                //System.out.println("Storing: " + c);
                out.writeByte(c);
                i++;
            } else {
                do {
                    charArrayWriter.write(c);
                    if (c >= 0xD800 && c <= 0xDBFF) {
                        if ((i + 1) < s.length()) {
                            int d = s.charAt(i + 1);
                            if (d >= 0xDC00 && d <= 0xDFFF) {
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                } while (i < s.length() && !dontNeedEncoding.get((c = s.charAt(i))));

                charArrayWriter.flush();
                String str = charArrayWriter.toString();
                byte[] ba = str.getBytes(charset);
                for (byte b : ba) {
                    out.writeByte('%');
                    char ch = Character.forDigit((b >> 4) & 0xF, 16);
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.writeByte(ch);
                    ch = Character.forDigit(b & 0xF, 16);
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.writeByte(ch);
                }
                charArrayWriter.reset();
            }
        }
    }
}
