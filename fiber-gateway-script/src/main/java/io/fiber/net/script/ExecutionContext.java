package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.run.ScriptExceptionNode;

public interface ExecutionContext {

    JsonNode root();

    Object attach();

    void returnVal(JsonNode value);

    void throwErr(ScriptExceptionNode error);

    default void throwErr(ScriptExecException error) {
        throwErr(ScriptExceptionNode.of(error));
    }

    JsonNode getArgVal(int idx);

    int getArgCnt();

    default boolean noArgs() {
        return getArgCnt() == 0;
    }

}
