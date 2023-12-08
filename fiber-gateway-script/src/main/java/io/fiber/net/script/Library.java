package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public interface Library {

    interface Constant {
        default boolean isConstExpr() {
            return true;
        }

        void get(ExecutionContext context);
    }


    interface Function {
        default boolean isConstExpr() {
            return true;
        }

        void call(ExecutionContext context, JsonNode... args);
    }

    interface DirectiveDef {
        Function findFunc(String directive, String function);
    }

    Library.Function findFunc(String name);

    default void markRootProp(String propName) {
    }

    Constant findConstant(String namespace, String key);

    DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals);

}
