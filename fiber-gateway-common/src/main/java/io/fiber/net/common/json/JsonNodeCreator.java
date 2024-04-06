package io.fiber.net.common.json;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Interface that defines common "creator" functionality implemented
 * both by {@link JsonNodeFactory} and {@link ContainerNode} (that is,
 * JSON Object and Array nodes).
 *
 * @since 2.3
 */
public interface JsonNodeCreator {
    // Enumerated/singleton types

    ValueNode booleanNode(boolean v);

    ValueNode nullNode();

// Not yet in 2.10, will be added in 3.0    
//     JsonNode missingNode();

    // Numeric types

    ValueNode numberNode(byte v);

    ValueNode numberNode(Byte value);

    ValueNode numberNode(short v);

    ValueNode numberNode(Short value);

    ValueNode numberNode(int v);

    ValueNode numberNode(Integer value);

    ValueNode numberNode(long v);

    ValueNode numberNode(Long value);

    ValueNode numberNode(BigInteger v);

    ValueNode numberNode(float v);

    ValueNode numberNode(Float value);

    ValueNode numberNode(double v);

    ValueNode numberNode(Double value);

    ValueNode numberNode(BigDecimal v);

    // Textual nodes

    ValueNode textNode(String text);

    // Other value (non-structured) nodes

    ValueNode binaryNode(byte[] data);

    ValueNode binaryNode(byte[] data, int offset, int length);


    ArrayNode arrayNode();

    /**
     * Factory method for constructing a JSON Array node with an initial capacity
     *
     * @since 2.8
     */
    ArrayNode arrayNode(int capacity);

    ObjectNode objectNode();
}
