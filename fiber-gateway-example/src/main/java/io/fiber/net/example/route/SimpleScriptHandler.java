package io.fiber.net.example.route;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.proxy.route.VarConfigSource;
import io.fiber.net.script.Script;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.Unpooled;

public class SimpleScriptHandler implements ScriptHandler {

    private final Injector injector;
    private final String name;
    private final Script script;
    private final VarConfigSource varConfigSource;
    private int[] varDefinitions;

    public SimpleScriptHandler(Injector injector,
                               String name,
                               Script script,
                               VarConfigSource varConfigSource) {
        this.injector = injector;
        this.name = name;
        this.script = script;
        this.varConfigSource = varConfigSource;
    }

    public String getRouterName() {
        return name;
    }

    @Override
    public void invoke(HttpExchange exchange) throws FiberException {
        exchange.checkMaxReqBodySize();

        script.exec(NullNode.getInstance(), exchange).subscribe((node, throwable) -> {
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

    @Override
    public VarConfigSource getVarConfigSource() {
        return varConfigSource;
    }

    public void setVarDefinitions(int[] varDefinitions) {
        this.varDefinitions = varDefinitions;
    }

    @Override
    public int[] getVarDefinitions() {
        return varDefinitions;
    }
}
