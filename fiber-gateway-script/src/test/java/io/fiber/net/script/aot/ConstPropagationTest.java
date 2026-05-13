package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

public class ConstPropagationTest {

    @Test
    public void shouldFoldBinaryConstants() {
        Assert.assertEquals(IntNode.valueOf(3), returnConst("return 1 + 2;"));
    }

    @Test
    public void shouldFoldUnaryConstants() {
        Assert.assertEquals(TextNode.valueOf("number"), returnConst("return typeof 1;"));
    }

    @Test
    public void shouldFoldNonObjectPropertyGetToMissing() {
        Assert.assertEquals(MissingNode.getInstance(), returnConst("let a = 1; return a.foo;"));
    }

    @Test
    public void shouldFoldPhiWithEqualConstantValues() {
        Cfg cfg = build("let a = 0; if ($.x) { a = 1; } else { a = 1; } return a;");

        Assert.assertEquals(0, countPhi(cfg));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldIgnoreUnreachablePhiInputFromFalseBranch() {
        Cfg cfg = build("let a = 1; if (false) { a = $.x; } return a;");

        Assert.assertEquals(0, countPhi(cfg));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldIgnoreUnreachablePhiInputFromTrueBranchElse() {
        Cfg cfg = build("let a = 1; if (true) { a = 2; } else { a = $.x; } return a;");

        Assert.assertEquals(0, countPhi(cfg));
        Assert.assertEquals(IntNode.valueOf(2), returnConst(cfg));
    }

    @Test
    public void shouldKeepExpressionWhenConstantEvaluationThrows() {
        Cfg cfg = build("return 'x' * true;");

        Assert.assertTrue(returnValue(cfg).getAssign() instanceof Binary);
    }

    private static ValueNode returnConst(String script) {
        return returnConst(build(script));
    }

    private static ValueNode returnConst(Cfg cfg) {
        Expr assign = returnValue(cfg).getAssign();
        Assert.assertTrue(assign instanceof LoadConst);
        return ((LoadConst) assign).getValueNode();
    }

    private static SsaValue returnValue(Cfg cfg) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Ret) {
                    return ((Ret) instruction).getValue();
                }
            }
        }
        throw new AssertionError("missing return");
    }

    private static Cfg build(String script) {
        Compiled compiled = CompilerNodeVisitor.compileFromScript(script, StdLibrary.getDefInstance());
        return new Cfg.Builder(compiled).build();
    }

    private static int countPhi(Cfg cfg) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            count += block.getPhiValues().size();
        }
        return count;
    }
}
