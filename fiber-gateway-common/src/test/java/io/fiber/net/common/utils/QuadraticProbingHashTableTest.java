package io.fiber.net.common.utils;

import io.netty.util.HashingStrategy;
import org.junit.Assert;
import org.junit.Test;

public class QuadraticProbingHashTableTest {

    @Test
    public void put() {
        QuadraticProbingHashTable<String, String> table = new QuadraticProbingHashTable<String, String>(15,
                HashingStrategy.JAVA_HASHER);

//        Map<String, String> table = new HashMap<>();

        for (int i = 0; i < 10000; i++) {
            Assert.assertNull(table.put("aaaaaa" + (13232 + i), "AAAAAA" + (13232 + i)));
        }

        for (int i = 0; i < 10000; i++) {
            String s = table.get("aaaaaa" + (13232 + i));
            Assert.assertEquals("AAAAAA" + (13232 + i), s);
        }

        Assert.assertEquals(10000, table.size());

    }
}