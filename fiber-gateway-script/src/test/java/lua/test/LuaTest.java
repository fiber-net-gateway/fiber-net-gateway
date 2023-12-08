package lua.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Literal;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;


public class LuaTest {


    ArrayNode arr;

    String s;

    @Before
    public void init() throws IOException {
        try (InputStream in = LuaTest.class.getResourceAsStream("/test.json")) {
            arr = (ArrayNode) JsonUtil.MAPPER.readTree(in);
        }
        try (InputStream in = LuaTest.class.getResourceAsStream("/test.js")) {
            s = IOUtils.toString(in);
        }
    }


    @Test
    public void run() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        for (JsonNode node : arr) {
            group.submit(() -> run0((ArrayNode) node)).syncUninterruptibly();
        }
        group.shutdownGracefully();
    }

    @Test
    public void run1() {
        Script compile = Script.compile("arrays.push([\"1\"], 3)");
        compile.exec(NullNode.getInstance());
    }

    private void run0(ArrayNode line) {
        Assert.assertEquals(3, line.size());
        Script compile = Script.compileExpression(line.get(0).textValue(), true);
        compile.exec(line.get(2)).subscribe((jsonNode, throwable) -> {
            Assert.assertEquals(line.get(1), jsonNode);
        });
    }

    @Test
    public void run2() throws InterruptedException {
        Timer timer = new Timer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Library library = getLibrary(timer, countDownLatch);


        Script compile = Script.compileExpression(" sleep(3000+sleep($ + 2000))", library, false);
        System.out.println("start.....");

        NioEventLoopGroup group = new NioEventLoopGroup();
        group.execute(() -> {
            compile.exec(IntNode.valueOf(3000)).subscribe((jsonNode, throwable) -> {
                System.out.println(jsonNode);
                Assert.assertEquals(8000, jsonNode.intValue());
            });
        });
        countDownLatch.await();
        timer.cancel();


        System.out.println("==========================");


    }


    @Test
    public void extracted() throws InterruptedException {
        ObjectNode m = JsonUtil.createObjectNode();
        m.put("a", 1);

        NioEventLoopGroup group = new NioEventLoopGroup();
        Timer timer = new Timer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Script script = Script.compile(s, getLibrary(timer, countDownLatch));
        group.execute(() -> {
            script.exec(m).subscribe((jsonNode, throwable) -> {
                System.out.println(jsonNode);
                if (throwable != null) {
                    throwable.printStackTrace();
                }
            });
        });
        countDownLatch.await();
        timer.cancel();
        group.shutdownGracefully();

    }

    private static Library getLibrary(Timer timer, CountDownLatch countDownLatch) {
        Library.Function sleep = new Library.Function() {
            @Override
            public void call(ExecutionContext context, JsonNode... args) {
                int s = args[0].intValue();
                Library.Function function = this;
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        context.returnVal(function, IntNode.valueOf(s));
                        countDownLatch.countDown();
                    }
                }, s);
            }

            @Override
            public boolean isConstExpr() {
                return false;
            }
        };

        Library.Function print = new Library.Function() {
            @Override
            public void call(ExecutionContext context, JsonNode... args) {
                System.out.print(System.currentTimeMillis() + ":");
                for (JsonNode arg : args) {
                    System.out.print(arg);
                    System.out.print("    ");
                }
                System.out.println();
                context.returnVal(this, NullNode.getInstance());
            }

            @Override
            public boolean isConstExpr() {
                return false;
            }
        };

        Library library = new Library() {

            @Override
            public Function findFunc(String name) {
                if (name.equals("sleep")) {
                    return sleep;
                }

                if (name.equals("print")) {
                    return print;
                }
                return null;
            }

            @Override
            public Constant findConstant(String namespace, String key) {
                return null;
            }

            @Override
            public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
                if (!type.equals("time")) {
                    return null;
                }
                String originalValue = literals.get(0).getLiteralValue().textValue();
                return (dn, fn) -> {
                    assert dn.equals(name);
                    assert fn.equals("get");

                    return new Function() {
                        @Override
                        public void call(ExecutionContext context, JsonNode... args) {
                            context.returnVal(this,
                                    TextNode.valueOf(new SimpleDateFormat(originalValue).format(new Date())));
                        }
                    };
                };
            }
        };
        return library;
    }


}
