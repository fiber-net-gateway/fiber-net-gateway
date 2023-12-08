package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class TryCatchStatement extends Statement {
    private final Identifier expVarName;

    private Statement tryBlock;
    private Statement catchBlock;

    public TryCatchStatement(int pos, Identifier expVarName, Statement tryBlock, Statement catchBlock) {
        super(pos);
        this.expVarName = expVarName;
        this.tryBlock = tryBlock;
        this.catchBlock = catchBlock;
    }

    public Identifier getExpVarName() {
        return expVarName;
    }

    public Statement getTryBlock() {
        return tryBlock;
    }


    public void setTryBlock(Statement tryBlock) {
        this.tryBlock = tryBlock;
    }

    public Statement getCatchBlock() {
        return catchBlock;
    }

    public void setCatchBlock(Statement catchBlock) {
        this.catchBlock = catchBlock;
    }


    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("try");
        tryBlock.toStringAST(sb);
        sb.append("catch(");
        sb.append(expVarName.getName());
        sb.append(")");
        catchBlock.toStringAST(sb);
    }
}
