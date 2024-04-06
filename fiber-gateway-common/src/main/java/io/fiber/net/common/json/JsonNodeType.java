package io.fiber.net.common.json;

/**
 * Enumeration of JSON types.
 * Covers all JSON types defined by <a
 * href="http://tools.ietf.org/html/rfc4627">RFC 4627</a> (array, boolean,
 * null, number, object and string) but also Jackson-specific types: binary,
 * missing and POJO; although does not distinguish between more granular
 * types.
 *
 * @see BinaryNode
 * @see MissingNode
 * @since 2.2
 */
public enum JsonNodeType {
    ARRAY,
    BINARY,
    BOOLEAN,
    MISSING,
    NULL,
    NUMBER,
    OBJECT,
    STRING,
    // for script
    EXCEPTION,
    ITERATOR
}
