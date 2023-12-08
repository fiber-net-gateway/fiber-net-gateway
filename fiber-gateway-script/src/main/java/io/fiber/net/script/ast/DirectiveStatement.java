package io.fiber.net.script.ast;

import io.fiber.net.script.Library;
import io.fiber.net.script.parse.NodeVisitor;

public class DirectiveStatement extends Statement {
    private final Identifier type;
    private final Identifier name;
    private final Library.DirectiveDef directiveDef;

    public DirectiveStatement(int pos, Identifier type, Identifier name, Library.DirectiveDef directiveDef) {
        super(pos);
        this.type = type;
        this.name = name;
        this.directiveDef = directiveDef;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        throw new UnsupportedOperationException("directive is not support parse");
    }

    public Library.DirectiveDef getDirectiveDef() {
        return directiveDef;
    }

    public Identifier getType() {
        return type;
    }

    public Identifier getName() {
        return name;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("directive ");
        sb.append(name.getName());
        sb.append(" from ");
        sb.append(type.getName());
        sb.append(';');
    }
}
