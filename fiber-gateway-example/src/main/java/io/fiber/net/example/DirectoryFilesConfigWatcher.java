package io.fiber.net.example;

import com.fasterxml.jackson.databind.JavaType;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.proxy.*;
import io.fiber.net.server.HttpEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectoryFilesConfigWatcher implements ConfigWatcher, Destroyable, Runnable {
    private static final Logger log = LoggerFactory.getLogger(DirectoryFilesConfigWatcher.class);
    private static final JavaType URL_ROUTE_TYPE = JsonUtil.MAPPER.getTypeFactory()
            .constructCollectionType(ArrayList.class, UrlRoute.class);

    volatile boolean stop;
    Map<String, Long> lastUpdate = new HashMap<>();
    private final File file;
    private final CorsConfig corsConfig = new CorsConfig();
    private HttpEngine engine;

    public DirectoryFilesConfigWatcher(File file) {
        this.file = file;
    }

    @Override
    public void startWatch(HttpEngine engine) throws Exception {
        this.engine = engine;
        engine.addHandlerRouter(engine.getInjector().getInstance(MetricRouteHandler.class));
        scanFile();
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy() {
        stop = true;
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void run() {
        while (!stop) {
            synchronized (this) {
                try {
                    wait(10000);
                } catch (InterruptedException ignore) {
                }
            }
            if (stop) {
                break;
            }
            scanFile();
        }
    }

    private void scanFile() {
        File[] files = file.listFiles();
        if (ArrayUtils.isEmpty(files)) {
            return;
        }
        for (File listFile : files) {
            if (!listFile.isFile()) {
                continue;
            }
            String name = parseName(listFile);
            long l = lastUpdate.getOrDefault(name, 0L);
            if (listFile.lastModified() <= l) {
                continue;
            }

            if (listFile.getName().endsWith(".json")) {
                addUrlMappingProject(listFile);
            } else if (listFile.getName().endsWith(".js")) {
                addJsProject(listFile, name);
            }
        }
    }

    private void addUrlMappingProject(File listFile) {
        String name = parseName(listFile);
        try {
            List<UrlRoute> routes = JsonUtil.MAPPER.readValue(listFile, URL_ROUTE_TYPE);
            engine.addHandlerRouter(LibProxyMainModule.createUrlMappingRouter(
                    engine.getInjector(),
                    name,
                    routes,
                    corsConfig));
            lastUpdate.put(name, listFile.lastModified());
        } catch (Exception e) {
            log.error("error init mapping project", e);
        }
    }

    private void addJsProject(File listFile, String name) {
        try {
            byte[] bytes = Files.readAllBytes(listFile.toPath());
            ScriptHandler project = LibProxyMainModule.createProject(engine.getInjector(),
                    name,
                    new String(bytes, StandardCharsets.UTF_8));
            engine.addHandlerRouter(project);
        } catch (Exception e) {
            log.error("error init project", e);
        }
        lastUpdate.put(name, listFile.lastModified());
    }

    private String parseName(File file) {
        String name = file.getName();
        int e = name.lastIndexOf('.');
        int s = name.lastIndexOf('/');

        if (e == -1) {
            e = name.length();
        }
        if (s == -1) {
            s = 0;
        }
        return name.substring(s, e);
    }
}
