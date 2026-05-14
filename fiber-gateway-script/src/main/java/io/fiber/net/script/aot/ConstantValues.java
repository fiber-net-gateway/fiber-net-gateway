package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;

final class ConstantValues {

    private ConstantValues() {
    }

    static ValueNode valueOf(SsaValue value) {
        Expr assign = value.getAssign();
        return assign instanceof LoadConst ? ((LoadConst) assign).getValueNode() : null;
    }

    static void replaceWithConst(Block block, Expr expr, ValueNode value) {
        LoadConst replacement = new LoadConst(block, expr.getPc(), value);
        expr.dropOperands();
        expr.getResult().replaceAssign(replacement);
        block.removeInstruction(expr);
    }
}
