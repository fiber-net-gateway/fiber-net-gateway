package io.fiber.net.proxy;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ProxyStartListener implements StartListener, Destroyable, Runnable {
    private static final Logger log = LoggerFactory.getLogger(ProxyStartListener.class);

    MainProxyModule proxyModule;
    volatile boolean stop;
    Map<String, Long> lastUpdate = new HashMap<>();

    private final File file;

    public ProxyStartListener(File file) {
        this.file = file;
    }

    @Override
    public void onStart(Engine engine) {
        Injector injector = engine.getInjector();
        proxyModule = injector.getInstance(MainProxyModule.class);
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
            if (!listFile.isFile() || !listFile.getName().endsWith(".js")) {
                continue;
            }
            String name = parseName(listFile);
            long l = lastUpdate.getOrDefault(name, 0L);
            long lasted = listFile.lastModified();
            if (lasted > l) {
                try {
                    byte[] bytes = Files.readAllBytes(listFile.toPath());
                    proxyModule.create(name, new String(bytes, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.error("error init project", e);
                }
                lastUpdate.put(name, lasted);
            }
        }
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
