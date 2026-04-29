package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public interface Library {

    interface Arguments {
        JsonNode getArgVal(int idx);

        int getArgCnt();

        default boolean noArgs() {
            return getArgCnt() == 0;
        }
    }

    interface AsyncHandle {
        void returnVal(JsonNode value);

        void throwErr(ScriptExecException error);
    }

    interface Constant {
        default boolean isConstExpr() {
            return true;
        }

        JsonNode get(ExecutionContext context) throws ScriptExecException;
    }


    interface Function {
        default FunctionSignature signature() {
            return null;
        }

        default boolean isConstExpr() {
            FunctionSignature signature = signature();
            return signature == null || signature.isConstExpr();
        }

        JsonNode call(ExecutionContext context, Arguments args) throws ScriptExecException;
    }

    interface AsyncConstant {
        void get(ExecutionContext context, AsyncHandle handle);
    }

    interface AsyncFunction {
        default FunctionSignature signature() {
            return null;
        }

        void call(ExecutionContext context, Arguments args, AsyncHandle handle);
    }


    interface DirectiveDef {
        ResolvedFunc resolveFunc(String directive, String function, FunctionCallArgs args);

    }

    ResolvedFunc resolveFunc(String name, FunctionCallArgs args);


    default void markRootProp(String propName) {
    }

    Object findConstant(String namespace, String key);

    DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals);

}
