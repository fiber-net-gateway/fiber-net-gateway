package io.fiber.net.script.ast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.parse.SpelMessage;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.parse.NodeVisitor;

public final class Literal extends ExpressionNode {

    private final String originalValue;
    private final JsonNode value;

    public Literal(String originalValue, int pos, JsonNode value) {
        super(pos);
        this.originalValue = originalValue;
        this.value = value;
    }


    public String getOriginalValue() {
        return this.originalValue;
    }


    @Override
    public String toString() {
        return getLiteralValue().toString();
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(this);
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    public JsonNode getLiteralValue() {
        return value;
    }

    public static Literal ofNull(int pos) {
        return new Literal("null", pos, NullNode.getInstance());
    }

//
//    public static Literal ofBoolean(int pos, boolean value) {
//        return new Literal(Boolean.toString(value), pos, BooleanNode.valueOf(value));
//    }

    public static Literal ofBoolean(int pos, boolean value) {
        return new Literal(Boolean.toString(value), pos, BooleanNode.valueOf(value));
    }

    public static Literal getIntLiteral(String numberToken, int pos, int radix) {
        try {
            int value = Integer.parseInt(numberToken, radix);
            return new Literal(numberToken, pos, IntNode.valueOf(value));
        } catch (NumberFormatException nfe) {
            throw new ParseException(nfe, pos >> 16, SpelMessage.NOT_AN_INTEGER, numberToken);
        }
    }

    public static Literal getLongLiteral(String numberToken, int pos, int radix) {
        try {
            long value = Long.parseLong(numberToken, radix);
            return new Literal(numberToken, pos, LongNode.valueOf(value));
        } catch (NumberFormatException nfe) {
            throw new ParseException(nfe, pos >> 16, SpelMessage.NOT_A_LONG, numberToken);
        }
    }

    public static Literal getRealLiteral(String numberToken, int pos, boolean isFloat) {
        try {
            if (isFloat) {
                float value = Float.parseFloat(numberToken);
                return new Literal(numberToken, pos, FloatNode.valueOf(value));
            } else {
                double value = Double.parseDouble(numberToken);
                return new Literal(numberToken, pos, DoubleNode.valueOf(value));
            }
        } catch (NumberFormatException nfe) {
            throw new ParseException(nfe, pos >> 16, SpelMessage.NOT_A_REAL, numberToken);
        }
    }

    public static Literal ofString(int pos, String value) {
        String s = new Escape(value).doEscape();
        return new Literal(value, pos, TextNode.valueOf(s));
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    private static class Escape {
        private final char[] toProcess;
        private int pos;
        private final int max;

        private int digitValue(char chr) {
            if ('0' <= chr && chr <= '9') {
                return chr - '0';
            } else if ('a' <= chr && chr <= 'f') {
                return chr - 'a' + 10;
            } else if ('A' <= chr && chr <= 'F') {
                return chr - 'A' + 10;
            } else {
                return 16;// Larger than any legal digit value
            }
        }

        private int scanEscape(char quote, StringBuilder sb) {
            char chr = toProcess[pos];
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
                    sb.append('a');
                    return 1;
                case 'b':
                    sb.append('\b');
                    return 1;
                case 'f':
                    sb.append('\f');
                    return 1;
                case 'n':
                    sb.append('\n');
                    return 1;
                case 'r':
                    sb.append('\r');
                    return 1;
                case 't':
                    sb.append('\t');
                    return 1;
                case 'v':
                    sb.append('\u000B');
                    return 1;
                case '\\':
                    sb.append('\\');
                    return 1;
                case '"':
                    sb.append('"');
                    return 1;
                case '\'':
                    sb.append('\'');
                    return 1;
                case '\r':
                    if (toProcess[pos + 1] == '\n') {
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
                case '\n':
                case '\u2028':
                case '\u2029':
                    return 1;
                default:
                    // not hit
                    throw new ParseException("", pos, SpelMessage.UNEXPECTED_ESCAPE_CHAR);
            }

            int l = length;
            int value = 0;
            for (tChar = toProcess[p]; l > 0 && tChar != quote; l--) {
                int digit = digitValue(tChar);
                if (digit >= base) {
                    break;
                }
                value = value * base + digit;
                tChar = toProcess[++p];
            }
            Assert.state(l == 0, "");
            sb.append((char) value);
            return p - pos;
        }

        public String doEscape() {
            StringBuilder sb = new StringBuilder(max);
            char quote = toProcess[pos];
            char tChr = toProcess[++pos];
            while (tChr != quote) {
                char chr = tChr;
                tChr = toProcess[++pos];
                if (chr == '\\') {
                    pos += scanEscape(quote, sb);
                    tChr = toProcess[pos];
                    continue;
                }
                sb.append(chr);
            }
            pos++;
            Assert.state(pos == max);
            return sb.toString();
        }

        public Escape(String value) {
            max = value.length();
            toProcess = new char[max + 1];
            value.getChars(0, max, toProcess, 0);
        }
    }


}
