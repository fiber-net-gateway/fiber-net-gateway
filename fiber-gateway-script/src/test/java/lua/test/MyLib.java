package lua.test;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.*;
import io.fiber.net.script.std.StdLibrary;

@ScriptLib(functionPrefix = "t")
public class MyLib extends StdLibrary {

    @ScriptFunction(name = "test1",
            params = {
                    @ScriptParam("t"),
                    @ScriptParam(value = "t2", variadic = true)
            })
    public static void test1(Library.AsyncHandle handle, Library.Arguments arguments) {
        int argCnt = arguments.getArgCnt();
        handle.returnVal(IntNode.valueOf(argCnt));
    }


    @ScriptFunction(name = "test2")
    public static void test2(Library.AsyncHandle handle,
                             @ScriptParam("t") JsonNode a,
                             @ScriptParam(value = "t2", variadic = true)
                             JsonNode... b) {
        handle.returnVal(IntNode.valueOf(b.length + 1));
    }

    @ScriptFunction(name = "test3")
    public static void test3(Library.AsyncHandle handle,
                             @ScriptParam("t") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
        handle.returnVal(IntNode.valueOf(2));
    }

    @ScriptFunction(name = "test4")
    public static JsonNode test4(
            @ScriptParam("t") JsonNode a,
            @ScriptParam(value = "b", optional = true, defaultValue = "3") JsonNode b) {
        return MissingNode.getInstance();
    }

    public MyLib() {
        putAsyncFunc("sleep", (context, args, handle) -> {
                    int ms = args.noArgs() ? 0 : args.getArgVal(0).asInt(3000);
                    if (ms == 0) {
                        handle.returnVal(IntNode.valueOf(ms));
                        return;
                    }
                    if (ms < 0) {
                        handle.throwErr(new ScriptExecException("ms must be >= 0"));
                        return;
                    }

                    if (ms >= 10000) {
                        Scheduler.current().schedule(() -> handle.throwErr(new ScriptExecException("ms < 10000")), 3000);
                        return;
                    }

                    Scheduler.current().schedule(
                            () -> handle.returnVal(IntNode.valueOf(ms)),
                            ms);
                }
        );
        putFunc("print", new Function() {
            @Override
            public boolean isConstExpr() {
                return false;
            }

            @Override
            public JsonNode call(ExecutionContext context, Arguments args) {
                int len = args.getArgCnt();
                for (int j = 0; j < len; j++) {
                    System.out.println("print: " + args.getArgVal(j));

                }
                return IntNode.valueOf(len);
            }
        });
        putFunc("panic", new Function() {
            @Override
            public boolean isConstExpr() {
                return false;
            }

            @Override
            public JsonNode call(ExecutionContext context, Arguments args) {
                throw new IllegalStateException("panic: " + args.getArgVal(0).asText("---"));
            }
        });

        ReflectLib.registerStatic(this, MyLib.class);
    }
}
