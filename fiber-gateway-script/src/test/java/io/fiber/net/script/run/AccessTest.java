package io.fiber.net.script.run;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import org.junit.Assert;
import org.junit.Test;

public class AccessTest {

    @Test
    public void shouldIndexTextByInteger() {
        Assert.assertEquals(TextNode.valueOf("b"), Access.indexGet(TextNode.valueOf("abc"), IntNode.valueOf(1)));
        Assert.assertSame(MissingNode.getInstance(), Access.indexGet(TextNode.valueOf("abc"), IntNode.valueOf(-1)));
        Assert.assertSame(MissingNode.getInstance(), Access.indexGet(TextNode.valueOf("abc"), IntNode.valueOf(3)));
        Assert.assertSame(MissingNode.getInstance(), Access.indexGet(TextNode.valueOf("abc"), TextNode.valueOf("1")));
    }
}
