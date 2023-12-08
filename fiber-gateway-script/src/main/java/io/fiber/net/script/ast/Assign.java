/*
 * Copyright 2002-2009 the original author or authors.
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

public class Assign extends ExpressionNode {

    private final MaybeLValue left;
    private final ExpressionNode right;

    public Assign(int pos, MaybeLValue left, ExpressionNode right) {
        super(pos);
        this.left = left;
        this.right = right;
        if (left instanceof VariableReference) {
            left.markLValue();
        }
    }

    public MaybeLValue getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        left.toStringAST(sb);
        sb.append("=");
        right.toStringAST(sb);
    }

    @Override
    public boolean isConstant() {
        return false;
    }
}
