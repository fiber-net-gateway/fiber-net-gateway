package io.fiber.net.script;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.ast.ExpressionNode;
import io.fiber.net.script.ast.ReturnStatement;
import io.fiber.net.script.parse.AotCompiledScript;
import io.fiber.net.script.parse.InterpretorScript;
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
        return InterpretorScript.createNonOptimise(script, block);
    }

    static Script compile(String script, Library library) throws ParseException {
        return compile(script, library, true);
    }

    static Script compile(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return InterpretorScript.create(script, block);
    }

    static Script compileExpression(String expression, boolean allowAssign) throws ParseException {
        return compileExpression(expression, StdLibrary.getDefInstance(), allowAssign);
    }

    static Script compileExpression(String expression, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        ExpressionNode ast = parser.parseExpression(expression);
        Block block = new Block(ast.getPos(), Collections.singletonList(new ReturnStatement(ast.getPos(), ast)), Block.Type.SCRIPT);
        return InterpretorScript.create(expression, block);
    }

    static Script aotCompile(String script) throws ParseException {
        return aotCompile(script, StdLibrary.getDefInstance());
    }

    static Script aotCompileWithoutAssign(String script) throws ParseException {
        return aotCompile(script, StdLibrary.getDefInstance(), false);
    }

    static Script aotCompileWithoutOptimization(String script) throws ParseException {
        return aotCompileWithoutOptimization(script, StdLibrary.getDefInstance(), true);
    }

    static Script aotCompileWithoutOptimization(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return AotCompiledScript.createNonOptimise(block);
    }

    static Script aotCompile(String script, Library library) throws ParseException {
        return aotCompile(script, library, true);
    }

    static Script aotCompile(String script, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        Block block = parser.parseScript(script);
        return AotCompiledScript.create(block);
    }

    static Script aotCompileExpression(String expression, boolean allowAssign) throws ParseException {
        return aotCompileExpression(expression, StdLibrary.getDefInstance(), allowAssign);
    }

    static Script aotCompileExpression(String expression, Library library, boolean allowAssign) throws ParseException {
        Parser parser = new Parser(library, allowAssign);
        ExpressionNode ast = parser.parseExpression(expression);
        Block block = new Block(ast.getPos(), Collections.singletonList(new ReturnStatement(ast.getPos(), ast)), Block.Type.SCRIPT);
        return AotCompiledScript.create(block);
    }


    default Maybe<JsonNode> exec(JsonNode root) {
        return exec(root, null);
    }

    Maybe<JsonNode> exec(JsonNode root, Object attach);

}
