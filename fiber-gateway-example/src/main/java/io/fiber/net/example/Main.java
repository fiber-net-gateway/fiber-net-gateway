package io.fiber.net.example;

import io.fiber.net.common.Engine;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.example.route.ScriptCodeSource;
import io.fiber.net.example.route.UrlHandlerManager;
import io.fiber.net.proxy.ConfigWatcher;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.LibProxyMainModule;
import io.fiber.net.proxy.ProxyModule;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.proxy.lib.HttpDynamicFunc;
import io.fiber.net.proxy.route.SimpleConstLibConfigure;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.server.ServerModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.io.File;
import java.nio.file.Files;

public class Main {

    public static void main(String[] args) throws Exception {
        if (ArrayUtils.isEmpty(args) || StringUtils.isEmpty(args[0])) {
            throw new IllegalArgumentException("onInit path is required");
        }
        File file = new File(args[0]);
        if (!file.exists() || !file.isDirectory()) {
            throw new IllegalArgumentException("onInit path must be directory");
        }
        Engine engine = LibProxyMainModule.createEngine(binder -> install(binder, file));

        Runtime.getRuntime().addShutdownHook(new Thread(engine::signalStop));
        engine.runLoop();
    }

    private static void install(Binder binder, File file) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        binder.bind(MeterRegistry.class, registry);
        binder.bind(MetricRouteHandler.class, new MetricRouteHandler(registry));


        binder.bindMultiBean(ProxyModule.class, binder1 -> {
            binder1.bindFactory(SimpleConstLibConfigure.class, i -> new SimpleConstLibConfigure());
            binder1.bindMultiBean(HttpLibConfigure.class, SimpleConstLibConfigure.class);
            binder1.bindMultiBean(HttpLibConfigure.class, new LibSleepFunc());
        });

        binder.bindMultiBean(ServerModule.class, binder1 -> {
            binder1.bindMultiBean(Interceptor.class, StatisticInterceptor.class);
            binder1.bindFactory(StatisticInterceptor.class, i -> new StatisticInterceptor(registry));
        });

        binder.bindFactory(UrlHandlerManager.class, UrlHandlerManager::new);
        binder.forceBindFactory(ConfigWatcher.class, i -> new DirectoryFilesConfigWatcher(file));
        File fileDir = new File(file, "file");
        fileDir.mkdirs();
        binder.forceBindFactory(ScriptCodeSource.class,
                i -> name -> new String(Files.readAllBytes(new File(fileDir, name + ".js").toPath())));
    }

    private static class LibSleepFunc implements HttpLibConfigure {

        @Override
        public void onInit(ExtensiveHttpLib lib) {
            lib.putAsyncFunc("sleep", context -> {
                int ms = context.noArgs() ? 0 : context.getArgVal(0).asInt(3000);
                Scheduler.current().schedule(() -> context.returnVal(IntNode.valueOf(ms)), ms);
            });
            lib.putConstant("$req", "uri",
                    new Library.Constant() {
                        @Override
                        public boolean isConstExpr() {
                            return false;
                        }

                        @Override
                        public JsonNode get(ExecutionContext context) throws ScriptExecException {
                            return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getUri());
                        }
                    });
        }
    }


}
