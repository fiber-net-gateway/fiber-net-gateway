package io.fiber.net.proxy;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.RefResourcePool;

import java.io.File;
import java.nio.file.Files;

public class UrlHandlerManager extends RefResourcePool<UrlHandlerManager.ScriptRef> {
    private final Injector injector;
    private final File path;

    public UrlHandlerManager(Injector injector, File path) {
        super("scripts");
        this.injector = injector;
        this.path = path;
    }

    @Override
    protected ScriptRef doCreateRef(String key) {
        try {
            byte[] bytes = Files.readAllBytes(new File(path, key + ".js").toPath());
            ScriptHandler handler = LibProxyMainModule.createProject(injector, key, new String(bytes));
            return new ScriptRef(this, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ScriptRef extends RefResourcePool.Ref {
        final ScriptHandler handler;

        protected ScriptRef(RefResourcePool<? extends Ref> pool, ScriptHandler handler) {
            super(pool);
            this.handler = handler;
        }

        @Override
        protected String refKey() {
            return handler.getRouterName();
        }

        @Override
        protected void doClose() {
            handler.destroy();
        }

        public ScriptHandler getHandler() {
            return handler;
        }
    }
}
