package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;

public interface ExecutionContext {

    JsonNode root();

    Object attach();

    void returnVal(JsonNode value);

    void throwErr(ScriptExecException error);

    JsonNode getArgVal(int idx);

    int getArgCnt();

    default boolean noArgs() {
        return getArgCnt() == 0;
    }

}
