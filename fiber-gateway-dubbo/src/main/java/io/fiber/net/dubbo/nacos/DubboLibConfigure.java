package io.fiber.net.dubbo.nacos;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.StringUtils;
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
    public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        if ("dubbo".equals(type)) {
            String service = literals.get(0).getLiteralValue().textValue();
            int timeout = literals.size() > 1 ? literals.get(1).getLiteralValue().intValue() : 1000;

            return new DubboServiceRefDef(service, timeout, injector);
        }
        return null;
    }

    private static class DubboServiceRefDef implements Library.DirectiveDef {
        private DubboRefManager.Reference ref;
        private final String service;
        private final int timeout;
        private final Injector injector;
        private DyFc dyFc;

        private DubboServiceRefDef(String service, int timeout, Injector injector) {
            this.service = service;
            this.timeout = timeout;
            this.injector = injector;
        }

        @Override
        public Library.Function findFunc(String directive, String function) {
            return null;
        }

        @Override
        public Library.AsyncFunction findAsyncFunc(String directive, String function) {
            if (ref == null) {
                DubboRefManager refManager = injector.getInstance(DubboRefManager.class);
                ref = refManager.ref(service, timeout);
            }

            if ("$dynamicInvoke".equals(function)) {
                if (dyFc == null) {
                    dyFc = new DyFc(ref);
                }
                return dyFc;
            }


            return new Fc(function, ref);
        }
    }

    private static class DyFc implements HttpDynamicFunc {
        private final DubboRefManager.Reference ref;

        private DyFc(DubboRefManager.Reference ref) {
            this.ref = ref;
        }

        @Override
        public void call(ExecutionContext context) {
            int cnt = context.getArgCnt();
            String mtd;
            if (cnt < 1 || StringUtils.isEmpty(mtd = context.getArgVal(0).textValue())) {
                context.throwErr(new ScriptExecException("$dynamicInvoke require method name", 500, DubboRefManager.DUBBO_ERR_NAME));
                return;
            }

            JsonNode args = cnt >= 2 ? context.getArgVal(1) : null;
            if (args != null && !args.isArray()) {
                context.throwErr(new ScriptExecException("$dynamicInvoke require arguments of array", 500, DubboRefManager.DUBBO_ERR_NAME));
                return;
            }

            ref.invoke(mtd, (ArrayNode) args).subscribe((jsonNode, throwable) -> {
                if (throwable != null) {
                    context.throwErr(ScriptExecException.fromThrowable(throwable));
                } else {
                    context.returnVal(jsonNode);
                }
            });
        }
    }

    private static class Fc implements HttpDynamicFunc {
        private final String method;
        private final DubboRefManager.Reference ref;

        private Fc(String method, DubboRefManager.Reference ref) {
            this.method = method;
            this.ref = ref;
        }

        @Override
        public void call(ExecutionContext context) {
            int argCnt = context.getArgCnt();
            Object[] args;
            if (argCnt > 0) {
                args = new Object[argCnt];
                for (int i = 0; i < argCnt; i++) {
                    args[i] = context.getArgVal(i);
                }
            } else {
                args = DubboRefManager.EMPTY;
            }
            ref.invoke(method, args).subscribe((jsonNode, throwable) -> {
                if (throwable != null) {
                    context.throwErr(ScriptExecException.fromThrowable(throwable));
                } else {
                    context.returnVal(jsonNode);
                }
            });
        }
    }
}
