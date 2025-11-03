package io.fiber.net.common;

import io.fiber.net.common.async.EngineScheduler;
import io.fiber.net.common.ext.EventSyncer;
import io.fiber.net.common.ext.LifecycleListener;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Engine implements Destroyable {
    private final EngineScheduler scheduler;
    private final Injector injector;
    private final EventSyncer syncer;
    private LifecycleListener[] listeners;
    private Map<String, Server<?>> serverMap;
    private CountDownLatch stopLatch;

    public Engine(Injector injector) {
        scheduler = EngineScheduler.init();
        this.injector = injector;
        syncer = injector.getInstance(EventSyncer.class);
    }

    public void installExt() throws Exception {
        listeners = injector.getInstances(LifecycleListener.class);
        Server<?>[] servers = injector.getInstances(Server.class);
        serverMap = new LinkedHashMap<>();
        for (Server<?> server : servers) {
            Server<?> old = serverMap.put(server.getName(), server);
            if (old != null) {
                throw new IllegalStateException("duplicate server name:" + server.getName());
            }
        }

        onEvent(LifecycleListener.Event.INIT);
        syncer.trySync();
        scheduler.runLoop();
        if (!scheduler.isSuspended()) {
            throw new IllegalStateException("engine not started successfully");
        }

        if (ArrayUtils.isNotEmpty(servers)) {
            for (Server<?> server : servers) {
                server.start();
            }
        }

        onEvent(LifecycleListener.Event.STARTED);

        stopLatch = new CountDownLatch(1);
    }

    public Server<?> getServer(String name) {
        return serverMap.get(name);
    }

    private void onEvent(LifecycleListener.Event event) throws Exception {
        if (ArrayUtils.isNotEmpty(listeners) && CollectionUtils.isNotEmpty(serverMap)) {
            for (LifecycleListener listener : listeners) {
                for (Server<?> server : serverMap.values()) {
                    listener.onEvent(server, event);
                }
            }
        }
    }

    public void runLoop() {
        try {
            scheduler.runLoop();
            if (!scheduler.isTerminated()) {
                throw new IllegalStateException("engine not exited successfully");
            }
        } finally {
            if (stopLatch != null) {
                stopLatch.countDown();
            }
            scheduler.detach();
        }
    }

    private void onExited() throws Exception {
        onEvent(LifecycleListener.Event.PRE_STOP);
        if (CollectionUtils.isNotEmpty(serverMap)) {
            for (Server<?> server : serverMap.values()) {
                server.stop();
            }
        }
        onEvent(LifecycleListener.Event.STOPPED);
        injector.destroy();
    }

    public void signalStop() {
        scheduler.execute(() -> {
            try {
                onExited();
            } catch (Exception e) {
                scheduler.abort(e);
                return;
            }
            scheduler.shutdown();
        });
        if (!scheduler.inLoop() && stopLatch != null) {
            try {
                stopLatch.await();
            } catch (Exception ignore) {
            }
        }
    }

    public final Injector getInjector() {
        return injector;
    }

    @Override
    public void destroy() {
        if (CollectionUtils.isNotEmpty(serverMap)) {
            for (Server<?> server : serverMap.values()) {
                server.destroy();
            }
            serverMap.clear();
        }
    }
}
