package io.fiber.net.dubbo.nacos;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.proxy.lib.HttpDynamicFunc;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public class DubboLibConfigure implements HttpLibConfigure {
    private final Injector injector;

    public DubboLibConfigure(Injector injector) {
        this.injector = injector;
    }

    @Override
    public void onInit(ExtensiveHttpLib lib) {

    }

    @Override
    public Library.Constant findConst(String namespace, String key) {
        return null;
    }


    @Override
    public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        if ("dubbo".equals(type)) {
            String service = literals.get(0).getLiteralValue().textValue();
            int timeout = literals.size() > 1 ? literals.get(1).getLiteralValue().intValue() : 1000;

            return new DubboServiceRefDef(service, timeout, injector);
        }
        return null;
    }

    private static class DubboServiceRefDef implements Library.DirectiveDef {
        private DubboReference ref;
        private final String service;
        private final int timeout;
        private final Injector injector;

        private DubboServiceRefDef(String service, int timeout, Injector injector) {
            this.service = service;
            this.timeout = timeout;
            this.injector = injector;
        }

        @Override
        public Library.Function findFunc(String directive, String function) {
            if (ref == null) {
                ref = injector.getInstance(DubboClient.class).getRef(service, timeout);
            }
            return new Fc(function, ref);
        }
    }

    private static class Fc implements HttpDynamicFunc {
        private final String method;
        private final DubboReference ref;

        private Fc(String method, DubboReference ref) {
            this.method = method;
            this.ref = ref;
        }

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            ref.invoke(method, args).subscribe((jsonNode, throwable) -> {
                if (throwable != null) {
                    context.throwErr(this, ScriptExecException.fromThrowable(throwable));
                } else {
                    context.returnVal(this, jsonNode);
                }
            });
        }
    }
}
