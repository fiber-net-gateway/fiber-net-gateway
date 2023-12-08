package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

import java.util.List;

public class Block extends Statement {
    private final List<Statement> statements;

    public Block(int pos, List<Statement> statements) {
        super(pos);
        this.statements = statements;
    }

    public List<Statement> getStatements() {
        return statements;
    }


    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        if (statements.isEmpty()) {
            return;
        }
        sb.append("{");
        for (Statement statement : statements) {
            statement.toStringAST(sb);
        }
        sb.append("}");
    }
}
