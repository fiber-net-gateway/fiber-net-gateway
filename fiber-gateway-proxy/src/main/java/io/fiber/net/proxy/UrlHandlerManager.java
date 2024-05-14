package io.fiber.net.proxy;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.RefResourcePool;

public class UrlHandlerManager extends RefResourcePool<UrlHandlerManager.ScriptRef> {
    private final Injector injector;
    private ScriptCodeSource scriptCodeSource;

    public UrlHandlerManager(Injector injector) {
        super("scripts");
        this.injector = injector;
    }

    @Override
    protected ScriptRef doCreateRef(String key) {
        if (scriptCodeSource == null) {
            scriptCodeSource = injector.getInstance(ScriptCodeSource.class);
        }
        try {
            ScriptHandler handler = LibProxyMainModule.createProject(injector, key, scriptCodeSource.getScript(key));
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
        protected void doClose() {
            handler.destroy();
        }

        public ScriptHandler getHandler() {
            return handler;
        }
    }
}
