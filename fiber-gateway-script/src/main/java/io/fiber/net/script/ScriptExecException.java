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

package io.fiber.net.script;


import io.fiber.net.common.FiberException;

public class ScriptExecException extends FiberException {
    public static final String ERROR_NAME = "JSON_EXPRESSION_EVALUATION";

    public ScriptExecException(String message) {
        super(message, 500, ERROR_NAME);
    }

    public ScriptExecException(String message, int code) {
        super(message, code, ERROR_NAME);
    }

    public ScriptExecException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public ScriptExecException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public ScriptExecException(Throwable cause, int code, String errorName) {
        super(cause, code, errorName);
    }

    public ScriptExecException(String message, Throwable cause) {
        super(message, cause, 500, ERROR_NAME);
    }

    public static ScriptExecException fromThrowable(Throwable cause) {
        if (cause instanceof ScriptExecException) {
            return (ScriptExecException) cause;
        }

        if (cause instanceof FiberException) {
            FiberException fe = (FiberException) cause;
            return new ScriptExecException(fe.getMessage(), cause, fe.getCode(),
                    fe.getErrorName());
        }

        return new ScriptExecException(cause.getMessage(), cause);

    }

}
