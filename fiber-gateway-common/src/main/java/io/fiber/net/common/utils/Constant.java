package io.fiber.net.common.utils;

import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.HttpMethod;

public class Constant {
    public static final String APP_NAME = "appName";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE_JSON_UTF8 = "application/json; charset=UTF-8";
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    public static final String X_POWERED_BY_HEADER = "X-Powered-By";
    public static final String X_FIBER_PROJECT_HEADER = "X-Fiber-Project";
    public static final String[] EMPTY_STR_ARR = new String[0];
    public static final byte[] EMPTY_BYTE_ARR = new byte[0];

    public static final TextNode[] METHOD_TEXTS = getMethodTexts();


    private static TextNode[] getMethodTexts(){
        HttpMethod[] values = HttpMethod.values();
        TextNode[] textNodes = new TextNode[values.length];
        for (int i = 0; i < values.length; i++) {
            textNodes[i] = TextNode.valueOf(values[i].name());
        }
        return textNodes;
    }
}
