package io.fiber.net.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

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
            {
                String uri = "/xx/xxx/xxxx" + sb;
                UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                    Assert.assertEquals(uri, path);
                    Assert.assertEquals(0, argsStart + argsEnd);
                });
            }
            {
                String uri = "/xx/xxx/xxxx" + sb + "vvv" + sb;
                UriCodec.parseComplexUri(uri, (path, argsStart, argsEnd) -> {
                    Assert.assertEquals(uri, path);
                    Assert.assertEquals(0, argsStart + argsEnd);
                });
            }
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

    private static class T implements UriCodec.Callback {
        String path;

        @Override
        public void accept(String path, int argsStart, int argsEnd) {
            this.path = path;
        }
    }


    private void assertCodec(String src) {
        String s = UriCodec.escapeUri(src);
        T callback = new T();
        UriCodec.parseComplexUri(s, callback);
        Assert.assertEquals(src, callback.path);
    }

    @Test
    public void t3() {
        assertCodec("/sfsafadfafa");
        assertCodec("/sfsafad🐾fafa");
        assertCodec("/sf张三safad🐾fafa");
        assertCodec("/ss🍖a把非诉讼公司的分公司；俄课题科技；苏联空军f🐶ad🐾f?af=a");
        assertCodec("/ss🍖a把非#诉讼公2432521--司的分公司；俄课题科技；苏联空军f🐶ad🐾f?af=a");

    }
}