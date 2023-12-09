package io.fiber.net.example;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.proxy.ConfigWatcher;
import io.fiber.net.proxy.LibProxyMainModule;
import io.fiber.net.server.EngineModule;
import io.fiber.net.server.HttpServer;
import io.fiber.net.server.ServerConfig;

import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {
        if (ArrayUtils.isEmpty(args) || StringUtils.isEmpty(args[0])) {
            throw new IllegalArgumentException("config path is required");
        }
        File file = new File(args[0]);
        if (!file.exists() || !file.isDirectory()) {
            throw new IllegalArgumentException("config path must be directory");
        }
        Injector injector = Injector.getRoot().createChild(new EngineModule(),
                new LibProxyMainModule(),
                b -> install(b, file));
        try {
            Engine engine = injector.getInstance(Engine.class);
            engine.installExt();

            HttpServer server = injector.getInstance(HttpServer.class);
            server.start(new ServerConfig(), engine);
            server.awaitShutdown();
        } finally {
            injector.destroy();
        }
    }

    private static void install(Binder binder, File file) {
        binder.forceBindFactory(ConfigWatcher.class, i -> new DirectoryFilesConfigWatcher(file));
    }
}
