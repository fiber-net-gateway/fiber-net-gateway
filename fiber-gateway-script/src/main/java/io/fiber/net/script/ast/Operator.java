package io.fiber.net.script.ast;


import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.parse.TokenKind;

public enum Operator {
    ADD("+"),
    MINUS("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%"),
    MATCH("~"),

    AND("&&"),
    OR("||"),

    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    EQ("=="),
    SEQ("==="),
    NE("!="),
    SNE("!=="),

    NOT("!"),

    TYPEOF("typeof"),
    IN("in"),
    ;
    private final String payload;

    Operator(String payload) {
        this.payload = payload;
    }

    public String getAstPayload() {
        if (this.ordinal() < TYPEOF.ordinal()) {
            return payload;
        }
        return " " + payload + " ";
    }

    public String getPayload() {
        return payload;
    }

    public static Operator fromToken(TokenKind token) {
        for (Operator value : VALUES) {
            if (value.ordinal() >= TYPEOF.ordinal()) {
                break;
            }
            if (token.getPayload().equals(value.payload)) {
                return value;
            }
        }
        throw new ParseException("unknown token " + token);
    }

    public static Operator fromIdentity(String identity) {
        for (int i = TYPEOF.ordinal(); i < VALUES.length; i++) {
            if (VALUES[i].payload.equals(identity)) {
                return VALUES[i];
            }
        }
        throw new ParseException("unknown token " + identity);
    }

    public static final Operator[] VALUES = Operator.values();
}
