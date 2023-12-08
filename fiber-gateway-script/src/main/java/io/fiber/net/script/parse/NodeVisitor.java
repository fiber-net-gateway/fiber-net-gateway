package io.fiber.net.script.parse;

import io.fiber.net.script.ast.*;

public interface NodeVisitor<T> {
    T visit(BinaryOperator binaryOperator);

    T visit(Ternary node);

    T visit(LogicRelationalExpression exp);

    T visit(Assign assign);

    T visit(Indexer indexer);

    T visit(Literal literal);

    T visit(ConstantVal constantVal);

    T visit(VariableReference variableReference);

    T visit(FunctionCall functionCall);

    T visit(UnaryOperator unaryOperator);

    T visit(ExpandArrArg expandArrArg);

    T visit(PropertyReference propertyReference);

    T visit(InlineList inlineList);

    T visit(InlineObject inlineObject);

    T visit(Block block);

    T visit(IfStatement ifStatement);

    T visit(ForeachStatement foreachStatement);

    T visit(VariableDeclareStatement variableDeclareStatement);

    T visit(ExpressionStatement expressionStatement);

    T visit(ContinueStatement breakStatement);

    T visit(BreakStatement breakStatement);

    T visit(ReturnStatement returnStatement);

    T visit(NoopNode noopNode);

    T visit(TryCatchStatement tryCatchStatement);

    T visit(ThrowStatement throwStatement);
}
