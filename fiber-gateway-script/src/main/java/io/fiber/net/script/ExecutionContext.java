package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;

public interface ExecutionContext {

    JsonNode root();

    Object attach();

    void returnVal(Library.Function fc, JsonNode value);

    void throwErr(Library.Function fc, ScriptExecException error);

    void returnVal(Library.Constant cn, JsonNode value);


    void throwErr(Library.Constant cn, ScriptExecException error);
}
