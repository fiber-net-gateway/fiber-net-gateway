package lua.test;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.NumericNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Script;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public class ScriptTest {
    @Test
    public void t1() throws Exception {

        InputStream resource = ScriptTest.class.getResourceAsStream("/test.json");
        JsonNode node = JsonUtil.readTree(resource);
        NioEventLoopGroup executors = new NioEventLoopGroup(1);
        CountDownLatch latch = new CountDownLatch(node.size());
        for (JsonNode jsonNode : node) {
            executors.execute(() -> {
                String script = jsonNode.get(0).textValue();
                Script compiled;

                try {
                    compiled = Script.compileExpression(script, true);
                } catch (Exception e) {
                    throw new IllegalStateException("cannot compile express: " + script, e);
                }
                Maybe<JsonNode> result = compiled.exec(jsonNode.get(2));
                result.subscribe((node1, throwable) -> {
                    try {
                        Assert.assertNull(throwable);
                        Assert.assertEquals(jsonNode.get(1), node1);
                    } catch (Throwable e) {
                        System.out.println(script);
                        throw e;
                    } finally {
                        latch.countDown();
                    }

                });
            });
        }
        latch.await();
        executors.shutdownGracefully().awaitUninterruptibly();
    }

    @Test
    public void t2() throws Exception {
        InputStream resource = ScriptTest.class.getResourceAsStream("/test.js");
        String string = IOUtils.toString(resource);
        MyLib library = new MyLib();
        Script script = Script.compile(string, library, true);

        JsonNode node = JsonUtil.readTree("{\"a\":2}");

        NioEventLoopGroup executors = new NioEventLoopGroup(1);
        CountDownLatch latch = new CountDownLatch(1);
        executors.execute(() -> {
            script.exec(node).subscribe((node1, throwable) -> {
                System.out.println("结果：" + node1);
                System.out.println("错误：" + throwable);
                latch.countDown();
            });
        });

        latch.await();
        executors.shutdownGracefully().awaitUninterruptibly();
    }


}
