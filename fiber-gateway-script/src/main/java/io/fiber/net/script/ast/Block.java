package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

import java.util.List;

public class Block extends Statement {
    public enum Type {
        SCRIPT,
        TRY,
        CATCH,
        FOR,
        IF,
        ELSE
    }

    private final List<Statement> statements;
    private Type type;

    public Block(int pos, List<Statement> statements, Type type) {
        super(pos);
        this.statements = statements;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
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
