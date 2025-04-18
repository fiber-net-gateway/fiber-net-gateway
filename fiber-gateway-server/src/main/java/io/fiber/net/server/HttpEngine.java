package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.FiberException;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ErrorInfo;

public class HttpEngine extends Engine<HttpExchange> {
    private ErrorHandler errorHandler;

    public HttpEngine(Injector injector) {
        super(injector);
    }

    @Override
    public void installExt() throws Exception {
        super.installExt();
        errorHandler = getInjector().getInstance(ErrorHandler.class);
    }

    @Override
    protected void runInternal(RouterHandler<HttpExchange> router, HttpExchange httpExchange) {
        if (router == null) {
            errorHandler.handleErr(httpExchange, new FiberException("router handler not found", 404, "ROUTER_NOT_FOUND"));
            return;
        }

        try {
            router.invoke(httpExchange);
        } catch (Exception e) {
            errorHandler.handleErr(httpExchange, e);
        }
    }

    static final WriteJsonErrHandler ERR_HANDLER = new WriteJsonErrHandler();

    static class WriteJsonErrHandler implements ErrorHandler {


        @Override
        public void handleErr(HttpExchange exchange, Throwable err) {
            exchange.discardReqBody();
            if (!(err instanceof FiberException)) {
                log.error("unknown error in handler ....", err);
            }
            if (!exchange.isResponseWrote()) {
                ErrorInfo ei = ErrorInfo.of(err);
                exchange.writeJson(ei.getStatus(), ei);
            }
        }
    }
}
