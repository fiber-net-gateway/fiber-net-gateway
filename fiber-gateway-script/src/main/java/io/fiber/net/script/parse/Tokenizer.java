/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fiber.net.script.parse;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.ast.Literal;

import java.util.ArrayList;
import java.util.List;

/**
 * Lex some input data into a stream of tokens that can then be parsed.
 *
 * @author Andy Clement
 * @author Phillip Webb
 * @since 3.0
 */
public class Tokenizer {

    String expressionString;
    String toProcess;
    int pos;
    int max;
    List<Token> tokens = new ArrayList<>();

    public Tokenizer(String inputdata) {
        this.expressionString = inputdata;
        this.pos = 0;
        this.max = inputdata.length();
        this.toProcess = inputdata;
    }

    public void process() {
        while (pos < max) {
            char ch = toProcess.charAt(pos);
            if (isAlphabetic(ch)) {
                lexIdentifier();
            } else {
                switch (ch) {
                    case '+':
                        if (isTwoCharToken(TokenKind.INC)) {
                            pushPairToken(TokenKind.INC);
                        } else {
                            pushCharToken(TokenKind.PLUS);
                        }
                        break;
                    case '_': // the other way to start an identifier
                        lexIdentifier();
                        break;
                    case '-':
                        if (isTwoCharToken(TokenKind.DEC)) {
                            pushPairToken(TokenKind.DEC);
                        } else {
                            pushCharToken(TokenKind.MINUS);
                        }
                        break;
                    case ':':
                        pushCharToken(TokenKind.COLON);
                        break;
                    case '.':
                        if (isThreeCharToken(TokenKind.EXPAND)) {
                            pushThreeToken(TokenKind.EXPAND);
                        } else {
                            pushCharToken(TokenKind.DOT);
                        }
                        break;
                    case ',':
                        pushCharToken(TokenKind.COMMA);
                        break;
                    case '*':
                        pushCharToken(TokenKind.STAR);
                        break;
                    case '/':
                        if (!skipComment()) {
                            pushCharToken(TokenKind.DIV);
                        }
                        break;
                    case '%':
                        pushCharToken(TokenKind.MOD);
                        break;
                    case ';':
                        pushCharToken(TokenKind.SEMICOLON);
                        break;
                    case '(':
                        pushCharToken(TokenKind.LPAREN);
                        break;
                    case ')':
                        pushCharToken(TokenKind.RPAREN);
                        break;
                    case '[':
                        pushCharToken(TokenKind.LSQUARE);
                        break;
                    case '#':
                        if (isTwoCharToken(TokenKind.PROJECT)) {
                            pushPairToken(TokenKind.PROJECT);
                        } else {
                            pushCharToken(TokenKind.HASH);
                        }
                        break;
                    case '^':
                        if (isTwoCharToken(TokenKind.SELECT)) {
                            pushPairToken(TokenKind.SELECT);
                        } else {
                            throw new ParseException(
                                    expressionString, pos,
                                    SpelMessage.error(SpelMessage.MISSING_CHARACTER, "^"));
                        }
                        break;
                    case '~':
                        pushCharToken(TokenKind.TILDE);
                        break;
                    case ']':
                        pushCharToken(TokenKind.RSQUARE);
                        break;
                    case '{':
                        pushCharToken(TokenKind.LCURLY);
                        break;
                    case '}':
                        pushCharToken(TokenKind.RCURLY);
                        break;
                    case '!':
                        if (isThreeCharToken(TokenKind.SNE)) {
                            pushThreeToken(TokenKind.SNE);
                        } else if (isTwoCharToken(TokenKind.NE)) {
                            pushPairToken(TokenKind.NE);
                        } else {
                            pushCharToken(TokenKind.NOT);
                        }
                        break;
                    case '=':
                        if (isThreeCharToken(TokenKind.SEQ)) {
                            pushThreeToken(TokenKind.SEQ);
                        } else if (isTwoCharToken(TokenKind.EQ)) {
                            pushPairToken(TokenKind.EQ);
                        } else {
                            pushCharToken(TokenKind.ASSIGN);
                        }
                        break;
                    case '&':
                        if (!isTwoCharToken(TokenKind.SYMBOLIC_AND)) {
                            throw new ParseException(
                                    expressionString, pos,
                                    SpelMessage.error(SpelMessage.MISSING_CHARACTER, "&"));
                        }
                        pushPairToken(TokenKind.SYMBOLIC_AND);
                        break;
                    case '|':
                        if (!isTwoCharToken(TokenKind.SYMBOLIC_OR)) {
                            throw new ParseException(
                                    expressionString, pos,
                                    SpelMessage.error(SpelMessage.MISSING_CHARACTER, "|"));
                        }
                        pushPairToken(TokenKind.SYMBOLIC_OR);
                        break;
                    case '?':
                        pushCharToken(TokenKind.QMARK);
                        break;
                    case '$':
                        lexIdentifier();
                        break;
                    case '>':
                        if (isTwoCharToken(TokenKind.GE)) {
                            pushPairToken(TokenKind.GE);
                        } else {
                            pushCharToken(TokenKind.GT);
                        }
                        break;
                    case '<':
                        if (isTwoCharToken(TokenKind.LE)) {
                            pushPairToken(TokenKind.LE);
                        } else {
                            pushCharToken(TokenKind.LT);
                        }
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        lexNumericLiteral(ch == '0');
                        break;
                    case ' ':
                    case '\t':
                    case '\r':
                    case '\n':
                        // drift over white space
                        pos++;
                        break;
                    case '\'':
                    case '"':
                        scanString();
                        break;
                    case '\\':
                        throw new ParseException(expressionString, pos, SpelMessage.UNEXPECTED_ESCAPE_CHAR.formatMessagePos(pos));
                    default:
                        throw new IllegalStateException("Cannot handle (" + (int) ch + ") '" + ch + "'");
                }
            }
        }
    }

    public static boolean isJSEOL(final char ch) {
        return ch == '\n'      // line feed
                || ch == '\r'      // carriage return (ctrl-m)
                || ch == '\u2028'  // line separator
                || ch == '\u2029'; // paragraph separator
    }

    private boolean skipComment() {
        int p = this.pos;
        int max = this.max;
        String toProcess = this.toProcess;
        if (++p < max) {
            char c = toProcess.charAt(p++);
            if (c == '/') {
                while (p < max && !isJSEOL(toProcess.charAt(p))) {
                    p++;
                }
                if (p + 1 < max && toProcess.charAt(p + 1) == '\n') {
                    p++;
                }
                this.pos = p + 1;
                return true;
            } else if (c == '*') {
                while (p < max) {
                    if (toProcess.charAt(p) == '*' && p + 1 < max && toProcess.charAt(p + 1) == '/') {
                        break;
                    }
                    p++;
                }
                this.pos = p + 2;
                return true;
            }
        }
        return false;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    private static int digitValue(char chr) {
        int v;
        if ('0' <= chr && chr <= '9') {
            v = chr - '0';
        } else if ('a' <= chr && chr <= 'f') {
            v = chr - 'a' + 10;
        } else if ('A' <= chr && chr <= 'F') {
            v = chr - 'A' + 10;
        } else {
            v = 16;
        }

        return v;// Larger than any legal digit value
    }

    private int scanEscape(char quote) {
        char chr = toProcess.charAt(pos);
        char tChar;
        int length, base;
        int p = pos;
        switch (chr) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
                //    Octal:
                length = 3;
                base = 8;
                break;
            case 'a':
            case '\'':
            case '"':
            case 'b':
            case 'f':
            case 'n':
            case 'r':
            case 't':
            case 'v':
            case '\\':
            case '\n':
            case '\u2028':
            case '\u2029':
                return 1;
            case '\r':
                if (toProcess.charAt(pos + 1) == '\n') {
                    return 2;
                }
                return 1;
            case 'x':
                p = pos + 1;
                length = 2;
                base = 16;
                break;
            case 'u':
                p = pos + 1;
                length = 4;
                base = 16;
                break;
            default:
                throw new ParseException(expressionString, pos, SpelMessage.UNEXPECTED_ESCAPE_CHAR);
        }

        int l = length;
        int value = 0;
        for (tChar = toProcess.charAt(p); l > 0 && tChar != quote; l--) {
            int digit = digitValue(tChar);
            if (digit >= base) {
                throw new ParseException(expressionString, p, SpelMessage.UNEXPECTED_ESCAPE_CHAR);
            }
            value = value * base + digit;
            tChar = toProcess.charAt(++p);
        }
        if (l != 0) {
            throw new ParseException(expressionString, p, SpelMessage.UNEXPECTED_ESCAPE_CHAR);
        }
        return p - pos;
    }

    private void scanString() {
        // " ' /
        char quote = toProcess.charAt(pos);
        int start = pos;
        char tChr = toProcess.charAt(++pos);
        try {
            while (tChr != quote) {
                char chr = tChr;
                if (chr == '\n' || chr == '\r' || chr == '\u2028' || chr == '\u2029') {
                    throw new ParseException(expressionString, pos, SpelMessage.NON_TERMINATING_DOUBLE_QUOTED_STRING);
                }
                tChr = toProcess.charAt(++pos);
                if (chr == '\\') {
                    pos += scanEscape(quote);
                    tChr = toProcess.charAt(pos);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ParseException(expressionString, pos, SpelMessage.NON_TERMINATING_DOUBLE_QUOTED_STRING);
        }
        if (pos > max) {
            throw new ParseException(expressionString, pos, SpelMessage.NON_TERMINATING_DOUBLE_QUOTED_STRING);
        }
        pos++;
        tokens.add(new Token(TokenKind.LITERAL_STRING, subarray(start, pos), start, pos));
    }

//	REAL_LITERAL :
//	  ('.' (DECIMAL_DIGIT)+ (EXPONENT_PART)? (REAL_TYPE_SUFFIX)?) |
//		((DECIMAL_DIGIT)+ '.' (DECIMAL_DIGIT)+ (EXPONENT_PART)? (REAL_TYPE_SUFFIX)?) |
//		((DECIMAL_DIGIT)+ (EXPONENT_PART) (REAL_TYPE_SUFFIX)?) |
//		((DECIMAL_DIGIT)+ (REAL_TYPE_SUFFIX));
//	fragment INTEGER_TYPE_SUFFIX : ( 'L' | 'l' );
//	fragment HEX_DIGIT : '0'|'1'|'2'|'3'|'4'|'5'|'6'|'7'|'8'|'9'|'A'|'B'|'C'|'D'|'E'|'F'|'a'|'b'|'c'|'d'|'e'|'f';
//
//	fragment EXPONENT_PART : 'e'  (SIGN)*  (DECIMAL_DIGIT)+ | 'E'  (SIGN)*  (DECIMAL_DIGIT)+ ;
//	fragment SIGN :	'+' | '-' ;
//	fragment REAL_TYPE_SUFFIX : 'F' | 'f' | 'D' | 'd';
//	INTEGER_LITERAL
//	: (DECIMAL_DIGIT)+ (INTEGER_TYPE_SUFFIX)?;

    private void lexNumericLiteral(boolean firstCharIsZero) {
        boolean isReal = false;
        int start = pos;
        char ch = pos + 1 < max ? toProcess.charAt(pos + 1) : 0;
        boolean isHex = ch == 'x' || ch == 'X';

        // deal with hexadecimal
        if (firstCharIsZero && isHex) {
            pos = pos + 1;
            do {
                pos++;
            } while (isHexadecimalDigit(toProcess.charAt(pos)));
            if (isChar('L', 'l')) {
                pushHexIntToken(subarray(start + 2, pos), true, start, pos);
                pos++;
            } else {
                pushHexIntToken(subarray(start + 2, pos), false, start, pos);
            }
            return;
        }

        // real numbers must have leading digits

        // Consume first part of number
        do {
            pos++;
        } while (pos < max && isDigit(toProcess.charAt(pos)));

        if (pos >= max) {
            pushIntToken(subarray(start, pos), false, start, pos);
            return;
        }

        // a '.' indicates this number is a real
        ch = toProcess.charAt(pos);
        if (ch == '.') {
            isReal = true;
            int dotpos = pos;
            // carry on consuming digits
            do {
                pos++;
            } while (pos < max && isDigit(toProcess.charAt(pos)));
            if (pos == dotpos + 1) {
                // the number is something like '3.'. It is really an int but may be
                // part of something like '3.toString()'. In this case process it as
                // an int and leave the dot as a separate token.
                pos = dotpos;
                pushIntToken(subarray(start, pos), false, start, pos);
                return;
            }
        }

        int endOfNumber = pos;
        if (pos >= max) {
            pushRealToken(subarray(start, endOfNumber), true, start, endOfNumber);
            return;
        }

        // Now there may or may not be an exponent

        // is it a long ?
        if (isChar('L', 'l')) {
            if (isReal) { // 3.4L - not allowed
                throw new ParseException(expressionString, start, SpelMessage.REAL_CANNOT_BE_LONG);
            }
            pushIntToken(subarray(start, endOfNumber), true, start, endOfNumber);
            pos++;
        } else if (isExponentChar(toProcess.charAt(pos))) {
            isReal = true; // if it wasn't before, it is now
            pos++;
            char possibleSign = toProcess.charAt(pos);
            if (isSign(possibleSign)) {
                pos++;
            }

            // exponent digits
            do {
                pos++;
            } while (isDigit(toProcess.charAt(pos)));
            boolean isFloat = false;
            if (isFloatSuffix(toProcess.charAt(pos))) {
                isFloat = true;
                endOfNumber = ++pos;
            } else if (isDoubleSuffix(toProcess.charAt(pos))) {
                endOfNumber = ++pos;
            }
            pushRealToken(subarray(start, pos), isFloat, start, pos);
        } else {
            ch = toProcess.charAt(pos);
            boolean isFloat = false;
            if (isFloatSuffix(ch)) {
                isReal = true;
                isFloat = true;
                endOfNumber = ++pos;
            } else if (isDoubleSuffix(ch)) {
                isReal = true;
                endOfNumber = ++pos;
            }
            if (isReal) {
                pushRealToken(subarray(start, endOfNumber), isFloat, start, endOfNumber);
            } else {
                pushIntToken(subarray(start, endOfNumber), false, start, endOfNumber);
            }
        }
    }

    // if this is changed, it must remain sorted
    private static final String[] alternativeOperatorNames = {"DIV", "EQ", "GE", "GT", "LE", "LT", "MOD", "NE", "NOT"};

    private void lexIdentifier() {
        int start = pos;
        do {
            pos++;
        } while (pos < max && isIdentifier(toProcess.charAt(pos)));
        char[] subarray = subarray(start, pos);
        tokens.add(new Token(TokenKind.IDENTIFIER, subarray, start, pos));
    }

    private void pushIntToken(char[] data, boolean isLong, int start, int end) {
        if (isLong) {
            tokens.add(new Token(TokenKind.LITERAL_LONG, data, start, end));
        } else {
            try {
                Literal.getIntLiteral(new String(data), start, 10);
                tokens.add(new Token(TokenKind.LITERAL_INT, data, start, end));
            } catch (Exception ignore) {
                // 有可能
                tokens.add(new Token(TokenKind.LITERAL_LONG, data, start, end));
            }
        }
    }

    private void pushHexIntToken(char[] data, boolean isLong, int start, int end) {
        if (data.length == 0) {
            if (isLong) {
                throw new ParseException(expressionString, start, SpelMessage.NOT_A_LONG, expressionString.substring(start, end + 1));
            } else {
                throw new ParseException(expressionString, start, SpelMessage.NOT_AN_INTEGER, expressionString.substring(start, end));
            }
        }
        if (isLong) {
            tokens.add(new Token(TokenKind.LITERAL_HEXLONG, data, start, end));
        } else {
            tokens.add(new Token(TokenKind.LITERAL_HEXINT, data, start, end));
        }
    }

    private void pushRealToken(char[] data, boolean isFloat, int start, int end) {
        if (isFloat) {
            tokens.add(new Token(TokenKind.LITERAL_REAL_FLOAT, data, start, end));
        } else {
            tokens.add(new Token(TokenKind.LITERAL_REAL, data, start, end));
        }
    }

    private char[] subarray(int start, int end) {
        char[] result = new char[end - start];
        toProcess.getChars(start, end, result, 0);
        return result;
    }

    /**
     * Check if this might be a two character token.
     */
    private boolean isThreeCharToken(TokenKind kind) {
        Assert.isTrue(kind.tokenChars.length == 3);
        Assert.isTrue(toProcess.charAt(pos) == kind.tokenChars[0]);
        if (pos + 3 > max) {
            return false;
        }
        return toProcess.charAt(pos + 1) == kind.tokenChars[1]
                && toProcess.charAt(pos + 2) == kind.tokenChars[2];
    }

    /**
     * Check if this might be a two character token.
     */
    private boolean isTwoCharToken(TokenKind kind) {
        Assert.isTrue(kind.tokenChars.length == 2);
        Assert.isTrue(toProcess.charAt(pos) == kind.tokenChars[0]);
        if (pos + 2 > max) {
            return false;
        }
        return toProcess.charAt(pos + 1) == kind.tokenChars[1];
    }

    /**
     * Push a token of just one character in length.
     */
    private void pushCharToken(TokenKind kind) {
        tokens.add(new Token(kind, pos, pos + 1));
        pos++;
    }

    /**
     * Push a token of two characters in length.
     */
    private void pushPairToken(TokenKind kind) {
        tokens.add(new Token(kind, pos, pos + 2));
        pos += 2;
    }

    /**
     * Push a token of two characters in length.
     */
    private void pushThreeToken(TokenKind kind) {
        tokens.add(new Token(kind, pos, pos + 3));
        pos += 3;
    }

    private void pushOneCharOrTwoCharToken(TokenKind kind, int pos, char[] data) {
        tokens.add(new Token(kind, data, pos, pos + kind.getLength()));
    }

    //	ID:	('a'..'z'|'A'..'Z'|'_'|'$') ('a'..'z'|'A'..'Z'|'_'|'$'|'0'..'9'|DOT_ESCAPED)*;
    private boolean isIdentifier(char ch) {
        return isAlphabetic(ch) || isDigit(ch) || ch == '_' || ch == '$';
    }

    private boolean isChar(char a, char b) {
        if (pos >= max) {
            return false;
        }
        char ch = toProcess.charAt(pos);
        return ch == a || ch == b;
    }

    private boolean isExponentChar(char ch) {
        return ch == 'e' || ch == 'E';
    }

    private boolean isFloatSuffix(char ch) {
        return ch == 'f' || ch == 'F';
    }

    private boolean isDoubleSuffix(char ch) {
        return ch == 'd' || ch == 'D';
    }

    private boolean isSign(char ch) {
        return ch == '+' || ch == '-';
    }

    private boolean isDigit(char ch) {
        if (ch > 255) {
            return false;
        }
        return (flags[ch] & IS_DIGIT) != 0;
    }

    private boolean isAlphabetic(char ch) {
        if (ch > 255) {
            return false;
        }
        return (flags[ch] & IS_ALPHA) != 0;
    }

    private boolean isHexadecimalDigit(char ch) {
        if (ch > 255) {
            return false;
        }
        return (flags[ch] & IS_HEXDIGIT) != 0;
    }

    private static final byte flags[] = new byte[256];
    private static final byte IS_DIGIT = 0x01;
    private static final byte IS_HEXDIGIT = 0x02;
    private static final byte IS_ALPHA = 0x04;

    static {
        for (int ch = '0'; ch <= '9'; ch++) {
            flags[ch] |= IS_DIGIT | IS_HEXDIGIT;
        }
        for (int ch = 'A'; ch <= 'F'; ch++) {
            flags[ch] |= IS_HEXDIGIT;
        }
        for (int ch = 'a'; ch <= 'f'; ch++) {
            flags[ch] |= IS_HEXDIGIT;
        }
        for (int ch = 'A'; ch <= 'Z'; ch++) {
            flags[ch] |= IS_ALPHA;
        }
        for (int ch = 'a'; ch <= 'z'; ch++) {
            flags[ch] |= IS_ALPHA;
        }
    }

}
