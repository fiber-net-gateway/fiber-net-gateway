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

public class Ternary extends ExpressionNode {

    private final ExpressionNode testVal;
    private final ExpressionNode trueVal;
    private final ExpressionNode falseVal;

    public Ternary(int pos, ExpressionNode testVal, ExpressionNode trueVal, ExpressionNode falseVal) {
        super(pos);
        this.testVal = testVal;
        this.trueVal = trueVal;
        this.falseVal = falseVal;
    }


    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        testVal.toStringAST(sb);
        sb.append('?');
        trueVal.toStringAST(sb);
        sb.append(':');
        falseVal.toStringAST(sb);
    }

    public ExpressionNode getTestVal() {
        return testVal;
    }

    public ExpressionNode getTrueVal() {
        return trueVal;
    }

    public ExpressionNode getFalseVal() {
        return falseVal;
    }

    @Override
    public boolean isConstant() {
        return testVal.isConstant() && trueVal.isConstant() && falseVal.isConstant();
    }
}
