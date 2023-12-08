package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.parse.CompiledScript;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.parse.Parser;
import io.fiber.net.script.std.StdLibrary;

public interface Script {

    static Script compile(String script) throws ParseException {
        return compile(script, StdLibrary.getDefInstance());
    }

    static Script compileWithoutAssign(String script) throws ParseException {
        return compile(script, StdLibrary.getDefInstance(), false);
    }

    static Script compile(String script, Library library) throws ParseException {
        return compile(script, library, true);
    }

    static Script compile(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return CompiledScript.create(script, block);
    }
    static Script compileExpression(String expression,  boolean allowAssign) throws ParseException {
        return compileExpression(expression,StdLibrary.getDefInstance(), allowAssign);
    }

    static Script compileExpression(String expression, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        return CompiledScript.create(expression, parser.parseExpression(expression));
    }

    default Maybe<JsonNode> exec(JsonNode root) {
        return exec(root, null);
    }

    Maybe<JsonNode> exec(JsonNode root, Object attach);

}
