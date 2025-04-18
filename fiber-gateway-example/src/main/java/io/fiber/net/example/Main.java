package io.fiber.net.example;

import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.proxy.*;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.server.HttpEngine;
import io.fiber.net.server.HttpExchange;
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
        HttpEngine engine = LibProxyMainModule.createEngine(binder -> install(binder, file));

        HttpClient httpClient = engine.getInjector().getInstance(HttpClient.class);
        engine.addHandlerRouter(new WebSocketHandler(httpClient));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> engine.getInjector().destroy()));
    }

    private static void install(Binder binder, File file) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        binder.bind(MeterRegistry.class, registry);
        binder.bind(MetricRouteHandler.class, new MetricRouteHandler(registry));
        binder.bindMultiBean(Interceptor.class, StatisticInterceptor.class);
        binder.bindFactory(StatisticInterceptor.class, i -> new StatisticInterceptor(registry));

        binder.bindMultiBean(ProxyModule.class, binder1 -> {
            binder1.bindMultiBean(HttpLibConfigure.class, new LibSleepFunc());
        });

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
        }
    }

    private static class WebSocketHandler implements RouterHandler<HttpExchange> {

        private HttpClient httpClient;

        public WebSocketHandler(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public String getRouterName() {
            return "websocket";
        }

        @Override
        public void invoke(HttpExchange exchange) {
            ClientExchange clientExchange = httpClient.refer(HttpHost.create("http://localhost:8080"));
            for (String name : exchange.getRequestHeaderNames()) {
                clientExchange.setHeader(name, exchange.getRequestHeaderList(name));
            }
            clientExchange.setUri(exchange.getUri());
            clientExchange.setReqBodyFunc(r -> exchange.readBodyUnsafe(), false);

            if ("upgrade".equalsIgnoreCase(exchange.getRequestHeader("Connection"))) {
                clientExchange.setUpgradeAllowed(true);
                clientExchange.setHeaderUnsafe("Upgrade", "websocket");
                clientExchange.setHeaderUnsafe("Connection", "Upgrade");
            }
            clientExchange.sendForResp().subscribe((r, e) -> {
                if (e != null) {
                    ErrorInfo info = ErrorInfo.of(e);
                    exchange.writeJson(info.getStatus(), info);
                    return;
                }
                for (String name : r.getHeaderNames()) {
                    exchange.setResponseHeader(name, r.getHeaderList(name));
                }

                if (r.isUpgraded()) {
                    String upgrade = r.getHeader("Upgrade");
                    UpgradedConnection downstream = exchange.upgrade(r.status(), upgrade, 30000);
                    UpgradedConnection upstream = r.upgradeConnection();
                    downstream.writeAndClose(upstream.readDataUnsafe(), true);
                    upstream.writeAndClose(downstream.readDataUnsafe(), true);
                } else {
                    exchange.writeRawBytes(r.status(), r.readRespBodyUnsafe());
                }
            });
        }

        @Override
        public void destroy() {

        }
    }
}
