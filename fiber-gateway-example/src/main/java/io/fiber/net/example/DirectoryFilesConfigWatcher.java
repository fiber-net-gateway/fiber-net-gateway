package io.fiber.net.example;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.json.JsonNode;
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

    volatile boolean stop;
    Map<String, Long> lastUpdate = new HashMap<>();
    private final File file;
    private File jsDir;
    private HttpEngine engine;
    private UrlHandlerManager urlHandlerManager;

    public DirectoryFilesConfigWatcher(File file) {
        this.file = file;
    }

    @Override
    public void startWatch(HttpEngine engine) throws Exception {
        this.engine = engine;
        jsDir = new File(file, "file");
        jsDir.mkdirs();
        urlHandlerManager = new UrlHandlerManager(engine.getInjector(), jsDir);
        scanFile();
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy() {
        stop = true;
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignore) {
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
        UrlMappingRouter router = new UrlMappingRouter(name);
        try {
            JsonNode node = JsonUtil.readTree(listFile);
            if (node.isEmpty() || !node.isArray()) {
                return;
            }

            RoutePathMatcher.Builder<ScriptHandler> builder = RoutePathMatcher.builder();
            List<UrlHandlerManager.ScriptRef> refs = new ArrayList<>();
            router.setRefs(refs);

            for (JsonNode jsonNode : node) {
                JsonNode method = jsonNode.get("method");
                JsonNode file = jsonNode.get("file");
                JsonNode url = jsonNode.get("url");
                if (method == null || file == null || url == null
                        || !method.isTextual() || !file.isTextual() || !url.isTextual()) {
                    return;
                }

                String m = method.textValue(), f = file.textValue(), u = url.textValue();
                HttpMethod hm;
                if ("*".equals(m)) {
                    hm = null;
                } else {
                    hm = HttpMethod.valueOf(m);
                }

                if (!new File(jsDir, f + ".js").exists()) {
                    return;
                }

                UrlHandlerManager.ScriptRef scriptRef = urlHandlerManager.getOrCreate(f);
                builder.addUrlHandler(hm, u, scriptRef.getHandler());
                refs.add(scriptRef);
            }

            RoutePathMatcher<ScriptHandler> pathMatcher = builder.build();
            router.setMatcher(pathMatcher);
            lastUpdate.put(name, listFile.lastModified());
        } catch (Exception e) {
            log.error("error init mapping project", e);
            router.destroy();
            return;
        }

        engine.addHandlerRouter(router);
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
