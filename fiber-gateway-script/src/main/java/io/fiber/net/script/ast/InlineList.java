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

public class InlineList extends ExpressionNode {

    private final ExpressionNode[] children;

    public InlineList(int pos, ExpressionNode... children) {
        super(pos);
        this.children = children;
    }

    public ExpressionNode[] getChildren() {
        return children;
    }


    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder s) {
        s.append('[');
        int count = children.length;
        for (int c = 0; c < count; c++) {
            if (c > 0) {
                s.append(',');
            }
            children[c].toStringAST(s);
        }
        s.append(']');
    }

    @Override
    public boolean isConstant() {
        for (ExpressionNode child : children) {
            if (!child.isConstant()) {
                return false;
            }
        }
        return true;
    }
}
