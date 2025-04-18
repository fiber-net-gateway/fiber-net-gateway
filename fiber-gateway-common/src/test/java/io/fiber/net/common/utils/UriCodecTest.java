package io.fiber.net.common.utils;

import org.junit.Assert;
import org.junit.Test;

public class UriCodecTest {

    @Test
    public void parseComplexUri() {
        {
            String uri = "/xx///xxx/xxxx/////?a=1&b=2";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/xxxx/", path);
                Assert.assertEquals("a=1&b=2", uri.substring(argsStart, argsEnd));
            });
        }

        {
            String uri = "/xx/xxx/xxxx/?a=1&b=2";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/xxxx/", path);
                Assert.assertEquals("a=1&b=2", uri.substring(argsStart, argsEnd));
            });
        }
        {
            String uri = "/xx/xxx/xxxx/?a=1&b=2#333";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/xxxx/", path);
                Assert.assertEquals("a=1&b=2", uri.substring(argsStart, argsEnd));
            });
        }
        {
            String uri = "/xx/a/..//xxx/./xxxx/./?a=1&b=2#44";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/xxxx/", path);
                Assert.assertEquals("a=1&b=2", uri.substring(argsStart, argsEnd));
            });
        }
        {
            String uri = "/xx/xxx/xxxx";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertSame("/xx/xxx/xxxx", path);
                Assert.assertEquals(0, argsStart + argsEnd);
            });
        }
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1024; i++) {
                sb.append("xxxxxxxxx");
            }
            String uri = "/xx/xxx/xxxx" + sb;
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals(uri, path);
                Assert.assertEquals(0, argsStart + argsEnd);
            });
        }
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("xxxxxxxxx");
            }
            String uri = "/xx/xxx/xxxx" + sb;
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals(uri, path);
                Assert.assertEquals(0, argsStart + argsEnd);
            });
        }
        {
            String uri = "/xx/xxx/%3f/xxxx";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/?/xxxx", path);
                Assert.assertEquals(UDecoder.convert(uri, false), path);
                Assert.assertEquals(0, argsStart + argsEnd);
            });
        }
        {
            String uri = "/xx/xxx/%2f/xxxx";
            UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                Assert.assertEquals("/xx/xxx/xxxx", path);
                Assert.assertEquals(0, argsStart + argsEnd);
            });
        }
    }
}