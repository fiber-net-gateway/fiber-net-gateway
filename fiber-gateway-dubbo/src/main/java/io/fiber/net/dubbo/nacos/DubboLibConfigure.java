package io.fiber.net.dubbo.nacos;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.proxy.lib.HttpDynamicFunc;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public class DubboLibConfigure implements HttpLibConfigure {
    private final DubboClient dubboClient;

    public DubboLibConfigure(DubboClient dubboClient) {
        this.dubboClient = dubboClient;
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
            String s = literals.get(0).getLiteralValue().textValue();
            int timeout = literals.size() > 1 ? literals.get(1).getLiteralValue().intValue() : 1000;
            return new DubboServiceRefDef(name, timeout, dubboClient);
        }
        return null;
    }

    private static class DubboServiceRefDef implements Library.DirectiveDef {
        private DubboReference ref;
        private final String name;
        private final int timeout;
        final DubboClient dubboClient;

        private DubboServiceRefDef(String name, int timeout, DubboClient dubboClient) {
            this.name = name;
            this.timeout = timeout;
            this.dubboClient = dubboClient;
        }

        @Override
        public Library.Function findFunc(String directive, String function) {
            if (ref == null) {
                ref = dubboClient.getRef(name, timeout);
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
