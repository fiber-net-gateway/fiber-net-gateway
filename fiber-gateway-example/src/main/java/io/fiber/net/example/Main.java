package io.fiber.net.example;

import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.proxy.ConfigWatcher;
import io.fiber.net.proxy.LibProxyMainModule;
import io.fiber.net.proxy.ScriptCodeSource;
import io.fiber.net.server.HttpEngine;
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> engine.getInjector().destroy()));
    }

    private static void install(Binder binder, File file) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        binder.bind(MeterRegistry.class, registry);
        binder.bind(MetricRouteHandler.class, new MetricRouteHandler(registry));
        binder.bindMultiBean(Interceptor.class, StatisticInterceptor.class);
        binder.bindFactory(StatisticInterceptor.class, i -> new StatisticInterceptor(registry));

        binder.forceBindFactory(ConfigWatcher.class, i -> new DirectoryFilesConfigWatcher(file));
        File fileDir = new File(file, "file");
        fileDir.mkdirs();
        binder.forceBindFactory(ScriptCodeSource.class,
                i -> name -> new String(Files.readAllBytes(new File(fileDir, name + ".js").toPath())));
    }
}
