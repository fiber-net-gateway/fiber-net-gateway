package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;

public class LoadConst extends Expr {
    final ValueNode valueNode;

    protected LoadConst(Block belongTo, int pc, ValueNode valueNode) {
        super(belongTo, pc);
        this.valueNode = valueNode;
    }

    public ValueNode getValueNode() {
        return valueNode;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.ofType(valueNode.getNodeType());
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }

    @Override
    public int effects() {
        return EFFECT_PURE;
    }
}
