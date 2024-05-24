package io.fiber.net.proxy;

import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.script.Script;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.Unpooled;

public class ScriptHandler implements RouterHandler<HttpExchange> {

    private final Injector injector;
    private final String name;
    private final Script script;

    public ScriptHandler(Injector injector,
                         String name, Script script) {
        this.injector = injector;
        this.name = name;
        this.script = script;
    }

    @Override
    public String getRouterName() {
        return name;
    }

    @Override
    public void invoke(HttpExchange exchange) {
        script.aotExec(NullNode.getInstance(), exchange).subscribe((node, throwable) -> {
            if (exchange.isResponseWrote()) {
                return;
            }
            if (throwable != null) {
                ErrorInfo info = ErrorInfo.of(throwable);
                exchange.writeJson(info.getStatus(), info);
            } else if (node != null) {
                exchange.writeJson(200, node);
            } else {
                exchange.writeRawBytes(204, Unpooled.EMPTY_BUFFER);
            }
        });
    }

    @Override
    public void destroy() {
        injector.destroy();
    }

    public Script getScript() {
        return script;
    }

    public Injector getInjector() {
        return injector;
    }
}
