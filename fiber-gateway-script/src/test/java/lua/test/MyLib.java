package lua.test;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.std.StdLibrary;

public class MyLib extends StdLibrary {
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
    }
}
