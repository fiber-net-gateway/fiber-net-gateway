package io.fiber.net.script.ast;

import io.fiber.net.script.Library;
import io.fiber.net.script.parse.NodeVisitor;

public class ConstantVal extends ExpressionNode {
    private final Object constant;
    private final String name;

    public ConstantVal(int pos, String name, Object constant) {
        super(pos);
        this.constant = constant;
        this.name = name;
    }

    public boolean isAsync() {
        return constant instanceof Library.AsyncFunction;
    }

    public Library.Constant getConstant() {
        return (Library.Constant) constant;
    }

    public Library.AsyncConstant getAsyncConstant() {
        return (Library.AsyncConstant) constant;
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
        return !isAsync() && getConstant().isConstExpr();
    }
}
