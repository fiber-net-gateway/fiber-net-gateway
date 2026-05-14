package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Unaries;

final class ConstantThrow {

    private ConstantThrow() {
    }

    static Template of(Instruction instruction) {
        try {
            if (instruction instanceof Binary) {
                evalBinary((Binary) instruction);
            } else if (instruction instanceof Unary) {
                evalUnary((Unary) instruction);
            } else {
                return null;
            }
            return null;
        } catch (ScriptExecException e) {
            return new Template(e.getMessage(), e.getCode(), e.getErrorName());
        }
    }

    private static void evalBinary(Binary binary) throws ScriptExecException {
        ValueNode left = ConstantValues.valueOf(binary.getLeft());
        ValueNode right = ConstantValues.valueOf(binary.getRight());
        if (left == null || right == null) {
            return;
        }
        switch (binary.getOp()) {
            case PLUS:
                Binaries.plus(left, right);
                return;
            case MINUS:
                Binaries.minus(left, right);
                return;
            case MULTIPLY:
                Binaries.multiply(left, right);
                return;
            case DIVIDE:
                Binaries.divide(left, right);
                return;
            case MOD:
                Binaries.modulo(left, right);
                return;
            default:
        }
    }

    private static void evalUnary(Unary unary) throws ScriptExecException {
        ValueNode value = ConstantValues.valueOf(unary.getMaterial());
        if (value == null) {
            return;
        }
        switch (unary.getOp()) {
            case PLUS:
                Unaries.plus(value);
                return;
            case MINUS:
                Unaries.minus(value);
                return;
            default:
        }
    }

    static final class Template {
        final String message;
        final int code;
        final String errorName;

        Template(String message, int code, String errorName) {
            this.message = message;
            this.code = code;
            this.errorName = errorName;
        }
    }
}
