/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fiber.net.script.ast;


import io.fiber.net.script.parse.NodeVisitor;

public class BinaryOperator extends ExpressionNode {

    private ExpressionNode left;
    private final Operator operator;
    private ExpressionNode right;

    public BinaryOperator(int pos, ExpressionNode left, Operator operator, ExpressionNode right) {
        super(pos);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }


    public final Operator getOperator() {
        return operator;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    public void setLeft(ExpressionNode left) {
        this.left = left;
    }

    public void setRight(ExpressionNode right) {
        this.right = right;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("(");
        sb.append(left);
        sb.append(operator.getPayload());
        sb.append(right);
        sb.append(")");
    }

    @Override
    public boolean isConstant() {
        return left.isConstant() && right.isConstant();
    }
}
