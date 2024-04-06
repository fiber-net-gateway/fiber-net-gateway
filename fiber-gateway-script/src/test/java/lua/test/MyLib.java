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
        putAsyncFunc("sleep", context -> {
                    int ms = context.noArgs() ? 0 : context.getArgVal(0).asInt(3000);
                    if (ms == 0) {
                        context.returnVal(IntNode.valueOf(ms));
                        return;
                    }
                    if (ms < 0) {
                        context.throwErr(new ScriptExecException("ms must be >= 0"));
                        return;
                    }

                    if (ms >= 10000) {
                        Scheduler.current().schedule(() -> context.throwErr(new ScriptExecException("ms < 10000")), 3000);
                        return;
                    }

                    Scheduler.current().schedule(
                            () -> context.returnVal(IntNode.valueOf(ms)),
                            ms);
                }
        );
        putFunc("print", new Function() {
            @Override
            public boolean isConstExpr() {
                return false;
            }

            @Override
            public JsonNode call(ExecutionContext context) {
                int len = context.getArgCnt();
                for (int j = 0; j < len; j++) {
                    System.out.println("print: " + context.getArgVal(j));

                }
                return NullNode.getInstance();
            }
        });
        putFunc("panic", new Function() {
            @Override
            public boolean isConstExpr() {
                return false;
            }

            @Override
            public JsonNode call(ExecutionContext context) {
                throw new IllegalStateException("panic: " + context.getArgVal(0).asText("---"));
            }
        });
    }
}