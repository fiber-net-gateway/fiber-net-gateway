package io.fiber.net.proxy;

import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpExchange;
import io.fiber.net.common.RequestHandlerRouter;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.script.Script;
import io.netty.buffer.Unpooled;

public class ScriptProjectRouter implements RequestHandlerRouter {

    private final Injector injector;
    private final String name;
    private Script script;

    public ScriptProjectRouter(Injector injector,
                               String name) {
        this.injector = injector;
        this.name = name;
    }

    public void setScript(Script script) {
        this.script = script;
    }

    @Override
    public String getRouterName() {
        return name;
    }

    @Override
    public void invoke(HttpExchange httpExchange) throws Exception {
        script.exec(NullNode.getInstance(), httpExchange).subscribe((node, throwable) -> {
            if (httpExchange.isResponseWrote()) {
                return;
            }
            try {
                if (throwable != null) {
                    ErrorInfo info = ErrorInfo.of(throwable);
                    httpExchange.writeJson(info.getStatus(), info);
                } else if (node != null) {
                    httpExchange.writeJson(200, node);
                } else {
                    httpExchange.writeRawBytes(204, Unpooled.EMPTY_BUFFER);
                }
            } catch (FiberException ignore) {
            }
        });
    }

    @Override
    public void destroy() {
        injector.destroy();
    }
}
