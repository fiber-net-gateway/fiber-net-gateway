package io.fiber.net.example.route;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.RefResourcePool;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.proxy.LibProxyMainModule;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.proxy.route.SimpleConstLibConfigure;
import io.fiber.net.proxy.route.VarConfigSource;
import io.fiber.net.script.Script;

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
            SimpleScriptHandler handler = createScriptHandler(injector, key, scriptCodeSource.getScript(key));
            return new ScriptRef(this, handler, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SimpleScriptHandler createScriptHandler(Injector injector, String projectName, String code) throws Exception {
        Assert.isTrue(StringUtils.isNotEmpty(code));
        Injector projectInjector = LibProxyMainModule.createProjectInjector(injector);
        try {
            Script compiled = compileScript(code, projectInjector);
            SimpleConstLibConfigure configure = projectInjector.getInstance(SimpleConstLibConfigure.class);
            VarConfigSource varConfigSource = configure.buildConfigSource();
            return new SimpleScriptHandler(projectInjector, projectName, compiled, varConfigSource);
        } catch (Throwable e) {
            projectInjector.destroy();
            throw e;
        }
    }

    static Script compileScript(String code, Injector injector) throws Exception {
        ExtensiveHttpLib library = new ExtensiveHttpLib(injector);
        return Script.aotCompile(code, library);
    }

    public static class ScriptRef extends RefResourcePool.Ref {
        final SimpleScriptHandler handler;

        protected ScriptRef(RefResourcePool<? extends Ref> pool, SimpleScriptHandler handler, String key) {
            super(pool, key);
            this.handler = handler;
        }

        @Override
        protected void doClose() {
            handler.destroy();
        }

        public SimpleScriptHandler getHandler() {
            return handler;
        }
    }
}
