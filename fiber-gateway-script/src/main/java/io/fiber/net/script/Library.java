package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public interface Library {

    interface Constant {
        default boolean isConstExpr() {
            return true;
        }

        JsonNode get(ExecutionContext context) throws ScriptExecException;
    }


    interface Function {
        default boolean isConstExpr() {
            return true;
        }

        JsonNode call(ExecutionContext context) throws ScriptExecException;
    }

    interface AsyncConstant {
        void get(ExecutionContext context);
    }

    interface AsyncFunction {
        void call(ExecutionContext context);
    }


    interface DirectiveDef {
        Function findFunc(String directive, String function);

        AsyncFunction findAsyncFunc(String directive, String function);
    }

    Object findFunc(String name);


    default void markRootProp(String propName) {
    }

    Object findConstant(String namespace, String key);

    DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals);

}
