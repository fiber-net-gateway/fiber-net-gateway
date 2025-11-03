package io.fiber.net.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

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

    @Test
    public void fromHex() {
        assertNotNull(HashUtils.fromHex("afasfad"));
        for (int i = 0; i < 1000; i++) {
            int i1 = ThreadLocalRandom.current().nextInt(1,10000);
            byte[] bytes = new byte[i1];
            ThreadLocalRandom.current().nextBytes(bytes);
            String hex = HashUtils.hex(bytes);
            Assert.assertArrayEquals(bytes, HashUtils.fromHex(hex));
            hex = hex.substring(0, hex.length() - 1);
            Assert.assertArrayEquals(Arrays.copyOf(bytes, bytes.length - 1), HashUtils.fromHex(hex));
        }
    }
}