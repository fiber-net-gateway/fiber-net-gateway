package io.fiber.net.script.ast;

import io.fiber.net.script.Library;
import io.fiber.net.script.parse.NodeVisitor;

public class ConstantVal extends ExpressionNode {
    private final Library.Constant constant;
    private final String name;

    public ConstantVal(int pos, String name, Library.Constant constant) {
        super(pos);
        this.constant = constant;
        this.name = name;
    }


    public Library.Constant getConstant() {
        return constant;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(name);
    }

    @Override
    public boolean isConstant() {
        return constant.isConstExpr();
    }
}
