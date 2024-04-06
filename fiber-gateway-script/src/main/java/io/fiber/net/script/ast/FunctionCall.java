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

import io.fiber.net.script.Library;
import io.fiber.net.script.parse.NodeVisitor;

public class FunctionCall extends ExpressionNode {

    private final String name;
    private final Object func;
    private final ExpressionNode[] args;

    public FunctionCall(Object func, String functionName, int pos, ExpressionNode... arguments) {
        super(pos);
        this.func = func;
        name = functionName;
        args = arguments;
    }

    public ExpressionNode[] getArgs() {
        return args;
    }

    public Library.Function getFunc() {
        return (Library.Function) func;
    }

    public boolean isAsync() {
        return func instanceof Library.AsyncFunction;
    }

    public Library.AsyncFunction getAsyncFunc() {
        return (Library.AsyncFunction) func;
    }

    public String getName() {
        return name;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(name);
        sb.append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                sb.append(",");
            args[i].toStringAST(sb);
        }
        sb.append(")");
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public boolean isConstant() {
        for (ExpressionNode arg : args) {
            if (!arg.isConstant()) {
                return false;
            }
        }
        return !isAsync() && getFunc().isConstExpr();
    }
}
