package io.fiber.net.common.utils;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class HashUtilsTest {

    @Test
    public void sha256() {

        assertNotNull(HashUtils.sha256("afasfad".getBytes()));
    }

    @Test
    public void sha1() {
        assertNotNull(HashUtils.sha1("afasfad".getBytes()));
    }
}