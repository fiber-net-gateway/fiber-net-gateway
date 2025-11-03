package io.fiber.net.proxy;

import io.fiber.net.common.Engine;
import io.fiber.net.common.Server;
import io.fiber.net.common.ext.EventSyncer;
import io.fiber.net.common.ext.LifecycleListener;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.ioc.*;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ConnectionFactory;
import io.fiber.net.http.DefaultHttpClient;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.proxy.gov.GovLibConfigure;
import io.fiber.net.proxy.lib.*;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.server.HttpServer;
import io.fiber.net.server.HttpServerModule;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class LibProxyMainModule implements Module {
    private static final Logger log = LoggerFactory.getLogger(LibProxyMainModule.class);

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
            binder.bindMultiBean(HttpLibConfigure.class, new RequestLibConfigure());
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
            ExtensiveHttpLib library = new ExtensiveHttpLib(injector);
            return Script.aotCompile(code, library);
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
        public Library.AsyncFunction findAsyncFunction(String name) {
            if ("req.tunnelProxy".equals(name)) {
                return new TunnelProxy(injector.getInstance(ConnectionFactory.class),
                        injector.getInstance(HttpClient.class));
            }
            return null;
        }

        @Override
        public Library.Function findFunction(String name) {
            if ("req.tunnelProxyAuth".equals(name)) {
                return new TunnelProxyAuth();
            }
            return null;
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

    private static class LoadConfigWatcherListener implements LifecycleListener {
        @Override
        public void onEvent(Server<?> server, Event event) throws Exception {
            if (event == Event.INIT && server instanceof HttpServer) {
                ConfigWatcher watcher = server.getInjector().getInstance(ConfigWatcher.class);
                watcher.startWatch((HttpServer) server);
            }
        }
    }

    static class EventLoopGroupHolder implements Destroyable, Initializable {
        private EventLoopGroup group;

        @Override
        public void init() {
            group = EpollAvailable.workerGroup();
        }

        public EventLoopGroup getGroup() {
            return group;
        }

        @Override
        public void destroy() {
            group.shutdownGracefully().syncUninterruptibly();
        }

    }


    @Override
    public void install(Binder binder) {
        binder.bindFactory(EventLoopGroupHolder.class, injector -> new EventLoopGroupHolder());
        binder.bindPrototype(EventLoopGroup.class, injector -> injector.getInstance(EventLoopGroupHolder.class).getGroup());
        binder.bindFactory(Engine.class, Engine::new);
        binder.bindFactory(EventSyncer.class, i -> new EventSyncer());

        binder.bindLink(HttpClient.class, DefaultHttpClient.class);
        binder.bindFactory(DefaultHttpClient.class, injector -> new DefaultHttpClient(injector.getInstance(EventLoopGroup.class)));
        binder.bindPrototype(ConnectionFactory.class, injector -> injector.getInstance(DefaultHttpClient.class).getConnectionFactory());
        binder.bindMultiBean(ProxyModule.class, SubModule.class);
        binder.bindFactory(SubModule.class, SubModule::new);
        binder.bindMultiBean(LifecycleListener.class, new LoadConfigWatcherListener());
        binder.bind(ConfigWatcher.class, ConfigWatcher.NOOP_WATCHER);
        binder.bindFactory(UrlHandlerManager.class, UrlHandlerManager::new);
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
        return subModule.createProject(projectName, routes, defCorsConfig);
    }

    public static CorsScriptHandler createProject(Server<?> injector,
                                                  String projectName,
                                                  String code,
                                                  CorsConfig corsConfig) throws Exception {
        ScriptHandler scriptHandler = createProject(injector.getInjector(), projectName, code);
        return CorsScriptHandler.create(scriptHandler, corsConfig);
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
        modules.add(new LibProxyMainModule());
        modules.add(new HttpServerModule());
        for (Module module : extModules) {
            modules.add(module);
        }

        Injector injector = Injector.getRoot().createChild(modules);
        Engine engine = injector.getInstance(Engine.class);
        try {
            engine.installExt();
        } catch (Throwable e) {
            log.error("error install engine", e);
            try {
                injector.destroy();
            } catch (Throwable ex) {
                log.warn("destroy engine error", ex);
            }
            throw e;
        }
        return engine;
    }

}
