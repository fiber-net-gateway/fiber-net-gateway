package io.fiber.net.example;

import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.dubbo.nacos.DubboModule;
import io.fiber.net.proxy.ConfigWatcher;
import io.fiber.net.proxy.LibProxyMainModule;
import io.fiber.net.server.HttpEngine;
import io.fiber.net.server.HttpServer;
import io.fiber.net.server.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (ArrayUtils.isEmpty(args) || StringUtils.isEmpty(args[0])) {
            throw new IllegalArgumentException("onInit path is required");
        }
        File file = new File(args[0]);
        if (!file.exists() || !file.isDirectory()) {
            throw new IllegalArgumentException("onInit path must be directory");
        }
        HttpEngine engine = LibProxyMainModule.createEngine(
                new DubboModule(),
                binder -> install(binder, file));

        try {
            HttpServer server = engine.getInjector().getInstance(HttpServer.class);
            server.start(new ServerConfig(), engine);
        } catch (Throwable e) {
            log.error("error start http server", e);
            engine.getInjector().destroy();
            throw e;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.getInjector().destroy();
        }));
    }

    private static void install(Binder binder, File file) {
        binder.forceBindFactory(ConfigWatcher.class, i -> new DirectoryFilesConfigWatcher(file));
    }
}
