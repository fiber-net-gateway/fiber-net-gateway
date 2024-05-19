package io.fiber.net.script;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.ast.ExpressionNode;
import io.fiber.net.script.ast.ReturnStatement;
import io.fiber.net.script.parse.CompiledScript;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.parse.Parser;
import io.fiber.net.script.std.StdLibrary;

import java.util.Collections;

public interface Script {

    static Script compile(String script) throws ParseException {
        return compile(script, StdLibrary.getDefInstance());
    }

    static Script compileWithoutAssign(String script) throws ParseException {
        return compile(script, StdLibrary.getDefInstance(), false);
    }

    static Script compileWithoutOptimization(String script) throws ParseException {
        return compileWithoutOptimization(script, StdLibrary.getDefInstance(), true);
    }

    static Script compileWithoutOptimization(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return CompiledScript.createNonOptimise(script, block);
    }

    static Script compile(String script, Library library) throws ParseException {
        return compile(script, library, true);
    }

    static Script compile(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return CompiledScript.create(script, block);
    }

    static Script compileExpression(String expression, boolean allowAssign) throws ParseException {
        return compileExpression(expression, StdLibrary.getDefInstance(), allowAssign);
    }

    static Script compileExpression(String expression, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        ExpressionNode ast = parser.parseExpression(expression);
        Block block = new Block(ast.getPos(),
                Collections.singletonList(new ReturnStatement(ast.getPos(), ast)), Block.Type.SCRIPT);
        return CompiledScript.create(expression, block);
    }

    default Maybe<JsonNode> exec(JsonNode root) {
        return exec(root, null);
    }

    default Maybe<JsonNode> aotExec(JsonNode root) throws Exception {
        return aotExec(root, null);
    }

    Maybe<JsonNode> exec(JsonNode root, Object attach);

    Maybe<JsonNode> aotExec(JsonNode root, Object attach);

}
