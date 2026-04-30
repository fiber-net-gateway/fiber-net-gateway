package io.fiber.net.proxy.lib;

import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.gov.GovLibConfigure;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.lib.ReflectDirective;
import io.fiber.net.script.parse.ParseException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ProxyAbiSignatureTest {

    @Test
    public void shouldCompileKnownProxyAbiSignatures() {
        compile("return req.getPath();");
        compile("return req.getHeader();");
        compile("return req.getHeader('host');");
        compile("return req.getQuery();");
        compile("return req.getQuery('a');");
        compile("return resp.setHeader('x-a', '1');");
        compile("return resp.send(200);");
        compile("return resp.send(200, 'ok');");
        compile("return req.tunnelProxy();");
        compile("return req.tunnelProxy(1000);");
        compile("return req.tunnelProxyAuth();");
        compile("return req.tunnelProxyAuth('Basic realm=\"x\"');");
        compile("directive h = http 'http://127.0.0.1'; return h.request();");
        compile("directive h = http 'http://127.0.0.1'; return h.proxyPass({flush:true});");
        compile("directive rl = rate_limiter '2/3s'; return rl.acquire();");
        compile("directive rl = rate_limiter '2/3s'; return rl.mustAcquire(100);");
    }

    @Test
    public void shouldRejectProxyAbiArgumentMismatchAtCompileTime() {
        assertNotCompile("return req.getPath(1);");
        assertNotCompile("return req.getHeader('a', 'b');");
        assertNotCompile("return req.readJson({});");
        assertNotCompile("return resp.setHeader('x');");
        assertNotCompile("return resp.send();");
        assertNotCompile("return req.tunnelProxy(1, 2);");
        assertNotCompile("directive h = http 'http://127.0.0.1'; return h.request({}, 1);");
        assertNotCompile("directive rl = rate_limiter '2/3s'; return rl.acquire(1, 2);");
    }

    private static void compile(String script) {
        Script.compileWithoutOptimization(script, library(), true);
    }

    private static void assertNotCompile(String script) {
        try {
            compile(script);
            Assert.fail("expected ABI argument mismatch: " + script);
        } catch (ParseException expected) {
            // expected
        }
    }

    private static Library library() {
        return new ExtensiveHttpLib(null, new HttpLibConfigure[]{
                new RequestLibConfigure(),
                new GovLibConfigure(),
                new HttpLibConfigure() {
                    @Override
                    public void onInit(ExtensiveHttpLib lib) {
                    }

                    @Override
                    public Library.AsyncFunction findAsyncFunction(String name) {
                        if ("req.tunnelProxy".equals(name)) {
                            return new TunnelProxy(null, null);
                        }
                        return null;
                    }

                    @Override
                    public Library.Function findFunction(String name) {
                        if ("req.tunnelProxyAuth".equals(name)) {
                            return new TunnelProxyAuth();
                        }
                        return null;
                    }

                    @Override
                    public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
                        if ("http".equals(type)) {
                            return ReflectDirective.of(new HttpFunc(null, null));
                        }
                        return null;
                    }
                }
        });
    }
}
