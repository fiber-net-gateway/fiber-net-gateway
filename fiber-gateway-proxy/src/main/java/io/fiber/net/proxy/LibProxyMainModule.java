package io.fiber.net.proxy;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.http.DefaultHttpClient;
import io.fiber.net.http.HttpClient;
import io.fiber.net.server.EngineModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class LibProxyMainModule implements Module {

    private static class SubModule implements ProxyModule {
        private final Injector engineInjector;
        private Injector projectInjector;

        public SubModule(Injector engineInjector) {
            this.engineInjector = engineInjector;
        }


        @Override
        public void install(Binder binder) {
            binder.bindPrototype(ProjectRouterBuilder.class, ProjectRouterBuilder::new);
        }

        synchronized void createProject(String projectName, String code) throws Exception {
            Injector injector;
            if (projectInjector != null) {
                injector = projectInjector.fork();
            } else {
                injector = projectInjector = engineInjector.createChild(engineInjector.getInstances(ProxyModule.class));
            }

            try {
                ProjectRouterBuilder builder = injector.getInstance(ProjectRouterBuilder.class);
                builder.setCode(code);
                builder.setName(projectName);
                injector.getInstance(Engine.class).addHandlerRouter(builder.build());
            } catch (Throwable e) {
                injector.destroy();
                throw e;
            }
        }
    }

    private static class ProxyStartListener implements StartListener {
        @Override
        public void onStart(Engine engine) throws Exception {
            ConfigWatcher watcher = engine.getInjector().getInstance(ConfigWatcher.class);
            watcher.startWatch(engine);
        }
    }

    @Override
    public void install(Binder binder) {
        binder.bindFactory(HttpClient.class, injector -> {
            EngineModule.EventLoopGroupHolder groupHolder = injector.getInstance(EngineModule.EventLoopGroupHolder.class);
            return new DefaultHttpClient(groupHolder.getGroup());
        });
        binder.bindMultiBean(ProxyModule.class, SubModule.class);
        binder.bindFactory(SubModule.class, SubModule::new);
        binder.bindMultiBean(StartListener.class, ProxyStartListener.class);
        binder.bind(ProxyStartListener.class, new ProxyStartListener());
        binder.bind(ConfigWatcher.class, ConfigWatcher.NOOP_WATCHER);
    }

    public static void createProject(Engine engine, String projectName, String code) throws Exception {
        SubModule subModule = engine.getInjector().getInstance(SubModule.class);
        assert subModule.engineInjector == engine.getInjector();
        subModule.createProject(projectName, code);
    }

    public static Engine createEngineWithSPI(ClassLoader loader) throws Exception {
        ServiceLoader<Module> serviceLoader = ServiceLoader.load(Module.class,
                loader == null ? Thread.currentThread().getContextClassLoader() : loader);
        return createEngine(serviceLoader);
    }

    public static Engine createEngine(Module... extModules) throws Exception {
        return createEngine(Arrays.asList(extModules));
    }

    public static Engine createEngine(Iterable<Module> extModules) throws Exception {
        List<Module> modules = new ArrayList<>();
        modules.add(new EngineModule());
        modules.add(new LibProxyMainModule());
        for (Module module : extModules) {
            modules.add(module);
        }

        Injector injector = Injector.getRoot().createChild(modules);
        Engine engine = injector.getInstance(Engine.class);
        try {
            engine.installExt();
        } catch (Throwable e) {
            injector.destroy();
            throw e;
        }
        return engine;
    }

}
