package io.fiber.net.common.utils;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.json.TextNode;

public class Constant {
    public static final String APP_NAME = "appName";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE_JSON_UTF8 = "application/json; charset=UTF-8";
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    public static final String X_POWERED_BY_HEADER = "X-Powered-By";
    public static final String X_FIBER_PROJECT_HEADER = "X-Fiber-Project";
    public static final String[] EMPTY_STR_ARR = new String[0];
    public static final Object[] EMPTY_ARRAY = new Object[0];
    public static final byte[] EMPTY_BYTE_ARR = new byte[0];

    public static final HttpMethod[] METHODS = HttpMethod.values();
    public static final TextNode[] METHOD_TEXTS = getMethodTexts();


    private static TextNode[] getMethodTexts(){
        TextNode[] textNodes = new TextNode[METHODS.length];
        for (int i = 0; i < METHODS.length; i++) {
            textNodes[i] = TextNode.valueOf(METHODS[i].name());
        }
        return textNodes;
    }
}
