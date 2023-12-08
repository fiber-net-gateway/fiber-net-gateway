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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AstUtils {
    protected static final Pattern NUM_REG = Pattern.compile("\\d+(\\.\\d+)?");
    public static final JsonNode[] EMPTY_JSON_NODES = new JsonNode[0];

    public static NumericNode tryToNumber(String text) {
        Matcher matcher = NUM_REG.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        if (StringUtils.isEmpty(matcher.group(1))) {
            long val = Long.parseLong(text);
            int intVal = (int) val;
            if (val == intVal) {
                return JsonUtil.numberNode(intVal);
            }
            return JsonUtil.numberNode(val);
        } else {
            return JsonUtil.numberNode(Double.parseDouble(text));
        }

    }

}
