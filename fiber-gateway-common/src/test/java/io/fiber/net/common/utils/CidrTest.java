package io.fiber.net.common.utils;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class CidrTest {

    @Test
    public void match() {
        String s = "192.168.0.0/16";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        assertTrue(cidr.match("192.168.1.1"));
        assertFalse(cidr.match("192.167.1.1"));
        assertFalse(cidr.match("194.167.1.1"));
        assertFalse(cidr.match("afdsfas"));
        assertFalse(cidr.match("afdsfas"));
    }

    @Test
    public void match2() {
        String s = "192.168.0.0/32";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        assertFalse(cidr.match("192.168.1.1"));
        assertFalse(cidr.match("192.167.1.1"));
        assertFalse(cidr.match("194.167.1.1"));
        assertFalse(cidr.match("afdsfas"));
        assertFalse(cidr.match("afdsfas"));
    }

    @Test
    public void match3() {
        String s = "192.168.0.0/0";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        assertTrue(cidr.match("192.168.1.1"));
        assertTrue(cidr.match("192.167.1.1"));
        assertTrue(cidr.match("194.167.1.1"));
        assertFalse(cidr.match("afdsfas"));
        assertFalse(cidr.match("afdsfas"));
    }

    @Test
    public void match4() {
        String s = "192.168.0.0/40";
        Cidr cidr = Cidr.parse(s);
        assertNull(cidr);
    }

    @Test
    public void match5() {
        String s = "192.168.0.0/16";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        String ip = "11192.168.1.122";
        assertTrue(cidr.match(ip, 2, ip.length() - 2));
    }

    @Test
    public void match6() {
        String s = "192.168.0.0/32";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        assertEquals(cidr, Cidr.parse("192.168.0.0"));
    }

    @Test
    public void contains0() {
        String s = "192.168.33.0/24";
        Cidr cidr = Cidr.parse(s);
        assertNotNull(cidr);
        assertTrue(cidr.contains(Cidr.parse("192.168.33.234")));
    }

    @Test
    public void contains1() {
        {
            String s = "192.168.33.0/16";
            Cidr cidr = Cidr.parse(s);
            assertNotNull(cidr);
            assertTrue(cidr.contains(Cidr.parse("192.168.33.234/32")));
            assertTrue(cidr.contains(Cidr.parse("192.168.33.234/24")));
        }
        {
            String s = "192.168.33.234/32";
            Cidr cidr = Cidr.parse(s);
            assertNotNull(cidr);
            assertFalse(cidr.contains(Cidr.parse("192.168.33.24/23")));
            assertFalse(cidr.contains(Cidr.parse("192.168.33.6/24")));
            assertTrue(cidr.contains(Cidr.parse("192.168.33.234")));
            Cidr parse = Cidr.parse("192.168.33.234");
            assertNotNull(parse);
            assertEquals(cidr, parse);
            assertTrue(parse.contains(cidr));
        }

    }

    @Test
    public void mat() {

        Cidr[] cidrs = Cidr.parseList(Arrays.asList("192.168.34.4/32", "192.167.34.3/24", "192.168.0.1/16", "192.168.34.3/24", "192.167.34.123/30"));
        assertEquals(2, cidrs.length);
        assertTrue(Cidr.matchAny(cidrs, "192.168.0.1"));
        assertTrue(Cidr.matchAny(cidrs, "192.167.34.3"));
        assertFalse(Cidr.matchAny(cidrs, "192.167.35.3"));
    }

}