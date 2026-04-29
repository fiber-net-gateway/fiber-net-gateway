package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;

import java.util.Arrays;

public final class FunctionSignature {
    private final String name;
    private final FunctionParam[] params;
    private final boolean variadic;
    private final int fixedCount;
    private final int requiredCount;
    private final boolean constExpr;

    public FunctionSignature(String name, boolean constExpr, FunctionParam... params) {
        this.name = name;
        this.params = params == null ? new FunctionParam[0] : Arrays.copyOf(params, params.length);
        this.constExpr = constExpr;

        boolean seenDefault = false;
        int fixed = this.params.length;
        if (fixed > 0 && this.params[fixed - 1].isVariadic()) {
            variadic = true;
            fixed--;
        } else {
            variadic = false;
        }

        int required = 0;
        for (int i = 0; i < this.params.length; i++) {
            FunctionParam param = this.params[i];
            if (param.isVariadic()) {
                if (i != this.params.length - 1) {
                    throw new IllegalArgumentException("variadic parameter must be last: " + name);
                }
                if (param.hasDefault()) {
                    throw new IllegalArgumentException("variadic parameter cannot have default value: " + name);
                }
                continue;
            }
            if (param.hasDefault()) {
                seenDefault = true;
            } else if (seenDefault) {
                throw new IllegalArgumentException("required parameter cannot follow default parameter: " + name);
            } else {
                required++;
            }
        }
        this.fixedCount = fixed;
        this.requiredCount = required;
    }

    public static FunctionSignature fixed(String name, FunctionParam... params) {
        return new FunctionSignature(name, true, params);
    }

    public static FunctionSignature fixed(String name, boolean constExpr, FunctionParam... params) {
        return new FunctionSignature(name, constExpr, params);
    }

    public static FunctionSignature variadic(String name, String restName) {
        return new FunctionSignature(name, true, FunctionParam.variadic(restName));
    }

    public boolean matches(FunctionCallArgs args) {
        int argc = args.getCount();
        if (!args.hasSpread()) {
            if (argc < requiredCount) {
                return false;
            }
            return variadic || argc <= fixedCount;
        }
        return variadic
                && args.getFirstSpreadIndex() >= fixedCount
                && args.getFirstSpreadIndex() >= requiredCount;
    }

    public boolean overlaps(FunctionSignature other) {
        if (variadic && other.variadic) {
            return true;
        }
        int max = variadic ? Integer.MAX_VALUE : fixedCount;
        int otherMax = other.variadic ? Integer.MAX_VALUE : other.fixedCount;
        return requiredCount <= otherMax && other.requiredCount <= max;
    }

    public JsonNode getDefaultValue(int idx) {
        if (idx >= 0 && idx < fixedCount) {
            FunctionParam param = params[idx];
            if (param.hasDefault()) {
                return param.getDefaultValue();
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public FunctionParam[] getParams() {
        return Arrays.copyOf(params, params.length);
    }

    public boolean isVariadic() {
        return variadic;
    }

    public int getFixedCount() {
        return fixedCount;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public boolean isConstExpr() {
        return constExpr;
    }

    public String display() {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            FunctionParam param = params[i];
            if (param.isVariadic()) {
                sb.append("...");
            }
            sb.append(param.getName());
            if (param.hasDefault()) {
                sb.append('=').append(param.getDefaultValue());
            }
        }
        return sb.append(')').toString();
    }
}
