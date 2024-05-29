package io.fiber.net.proxy;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.utils.*;
import io.fiber.net.dubbo.nacos.DubboClient;
import io.fiber.net.dubbo.nacos.DubboConfig;
import io.fiber.net.dubbo.nacos.DubboRefManager;
import io.fiber.net.http.DefaultHttpClient;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.proxy.gov.GovLibConfigure;
import io.fiber.net.proxy.lib.DubboLibConfigure;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.proxy.lib.HttpFunc;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.server.EngineModule;
import io.fiber.net.server.HttpEngine;
import io.fiber.net.server.HttpServerStartListener;
import io.netty.channel.EventLoopGroup;

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
            binder.bindMultiBean(HttpLibConfigure.class, ProxyHttpLibConfigure.class);
            binder.bindPrototype(ProxyHttpLibConfigure.class, ProxyHttpLibConfigure::new);
            binder.bindMultiBean(HttpLibConfigure.class, GovLibConfigure.class);
            binder.bind(GovLibConfigure.class, new GovLibConfigure());
            binder.bindPrototype(DubboLibConfigure.class, DubboLibConfigure::new);
            binder.bindMultiBean(HttpLibConfigure.class, DubboLibConfigure.class);
            binder.bindFactory(DubboRefManager.class, DubboRefManager::new);
        }

        synchronized ScriptHandler createProject(String projectName, String code) throws Exception {
            Assert.isTrue(StringUtils.isNotEmpty(code));
            Injector injector = createProjectInjector();
            try {
                Script compiled = compileScript(code, injector);
                return new ScriptHandler(injector, projectName, compiled);
            } catch (Throwable e) {
                injector.destroy();
                throw e;
            }
        }

        synchronized UrlMappingRouter createProject(String projectName,
                                                    List<UrlRoute> routes,
                                                    CorsConfig defCorsConfig) throws Exception {
            Assert.isTrue(CollectionUtils.isNotEmpty(routes));
            UrlHandlerManager urlHandlerManager = engineInjector.getInstance(UrlHandlerManager.class);
            UrlMappingRouter.Builder builder = UrlMappingRouter.builder(urlHandlerManager);
            return builder.setName(projectName)
                    .setRoutes(routes)
                    .setDefaultCorsConfig(defCorsConfig)
                    .build();
        }

        static Script compileScript(String code, Injector injector) throws Exception {
            HttpLibConfigure[] configures = injector.getInstances(HttpLibConfigure.class);

            ExtensiveHttpLib library = new ExtensiveHttpLib(injector, configures);
            if (ArrayUtils.isNotEmpty(configures)) {
                for (HttpLibConfigure configure : configures) {
                    configure.onInit(library);
                }
            }
            return Script.compile(code, library);
        }

        private Injector createProjectInjector() {
            Injector injector;
            if (projectInjector != null) {
                injector = projectInjector.fork();
            } else {
                injector = projectInjector = engineInjector.createChild(engineInjector.getInstances(ProxyModule.class));
            }
            return injector;
        }
    }


    private static class ProxyHttpLibConfigure implements HttpLibConfigure {

        private final Injector injector;

        public ProxyHttpLibConfigure(Injector injector) {
            this.injector = injector;
        }

        @Override
        public void onInit(ExtensiveHttpLib lib) {
        }

        @Override
        public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
            if ("http".equals(type)) {
                HttpHost httpHost = HttpHost.create(literals.get(0).getLiteralValue().textValue());
                return new HttpFunc(httpHost, injector.getInstance(HttpClient.class));
            }
            return null;
        }
    }

    private static class ProxyStartListener extends HttpServerStartListener {
        @Override
        protected void beforeServerStart(HttpEngine engine) throws Exception {
            ConfigWatcher watcher = engine.getInjector().getInstance(ConfigWatcher.class);
            watcher.startWatch(engine);
        }
    }

    private static DubboConfig defaultConfig() {
        String registry = SystemPropertyUtil.get("fiber.dubbo.registry");
        if (StringUtils.isEmpty(registry)) {
            throw new IllegalStateException("no dubbo registry configured. -Dfiber.dubbo.registry=");
        }
        DubboConfig config = new DubboConfig();
        config.setRegistryAddr(registry);
        config.setApplicationName(SystemPropertyUtil.get("fiber.dubbo.appName", config.getApplicationName()));
        config.setProtocol(SystemPropertyUtil.get("fiber.dubbo.protocol", config.getProtocol()));
        return config;
    }


    @Override
    public void install(Binder binder) {
        binder.bindFactory(HttpClient.class, injector -> new DefaultHttpClient(injector.getInstance(EventLoopGroup.class)));
        binder.bindMultiBean(ProxyModule.class, SubModule.class);
        binder.bindFactory(SubModule.class, SubModule::new);
        binder.forceBind(HttpServerStartListener.class, new ProxyStartListener());
        binder.bind(ConfigWatcher.class, ConfigWatcher.NOOP_WATCHER);
        binder.bindFactory(UrlHandlerManager.class, UrlHandlerManager::new);
        binder.bindFactory(DubboClient.class, injector -> new DubboClient(defaultConfig()));
    }

    public static ScriptHandler createProject(Injector injector, String projectName, String code) throws Exception {
        SubModule subModule = injector.getInstance(SubModule.class);
        assert subModule.engineInjector == injector;
        return subModule.createProject(projectName, code);
    }

    public static UrlMappingRouter createUrlMappingRouter(Injector injector,
                                                          String projectName,
                                                          List<UrlRoute> routes,
                                                          CorsConfig defCorsConfig) throws Exception {
        SubModule subModule = injector.getInstance(SubModule.class);
        assert subModule.engineInjector == injector;
        return subModule.createProject(projectName, routes, defCorsConfig);
    }

    public static CorsScriptHandler createProject(Injector injector,
                                                  String projectName,
                                                  String code,
                                                  CorsConfig corsConfig) throws Exception {
        ScriptHandler scriptHandler = createProject(injector, projectName, code);
        return CorsScriptHandler.create(scriptHandler, corsConfig);
    }

    public static HttpEngine createEngineWithSPI(ClassLoader loader) throws Exception {
        ServiceLoader<Module> serviceLoader = ServiceLoader.load(Module.class,
                loader == null ? Thread.currentThread().getContextClassLoader() : loader);
        return createEngine(serviceLoader);
    }

    public static HttpEngine createEngine(Module... extModules) throws Exception {
        return createEngine(Arrays.asList(extModules));
    }

    public static HttpEngine createEngine(Iterable<Module> extModules) throws Exception {
        List<Module> modules = new ArrayList<>();
        modules.add(new LibProxyMainModule());
        modules.add(new EngineModule());
        for (Module module : extModules) {
            modules.add(module);
        }

        Injector injector = Injector.getRoot().createChild(modules);
        HttpEngine engine = (HttpEngine) injector.getInstance(Engine.class);
        try {
            engine.installExt();
        } catch (Throwable e) {
            injector.destroy();
            throw e;
        }
        return engine;
    }

}
