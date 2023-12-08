package io.fiber.net.proxy;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.http.DefaultHttpClient;
import io.fiber.net.http.HttpClient;
import io.fiber.net.server.EngineModule;
import io.fiber.net.server.HttpServer;

import java.io.File;

public class MainProxyModule implements ProxyModule {

    public static void installProxy(Binder binder) {
        binder.bindFactory(HttpClient.class, injector -> {
            EngineModule.EventLoopGroupHolder groupHolder = injector.getInstance(EngineModule.EventLoopGroupHolder.class);
            return new DefaultHttpClient(groupHolder.getGroup());
        });
        binder.bindMultiBean(ProxyModule.class, MainProxyModule.class);
        binder.bindFactory(MainProxyModule.class, MainProxyModule::new);
        binder.bindMultiBean(StartListener.class, ProxyStartListener.class);
        binder.bind(ProxyStartListener.class, new ProxyStartListener());
    }

    private final Injector engineInjector;
    private Injector projectInjector;

    MainProxyModule(Injector injector) {
        engineInjector = injector;
    }


    @Override
    public void install(Binder binder) {
        binder.bindPrototype(ProjectRouterBuilder.class, ProjectRouterBuilder::new);
    }

    public synchronized void create(String projectName,File file) throws Exception {
        Injector injector;
        if (projectInjector != null) {
            injector = projectInjector.fork();
        } else {
            injector = projectInjector = engineInjector.createChild(engineInjector.getInstances(ProxyModule.class));
        }

        try {
            ProjectRouterBuilder builder = injector.getInstance(ProjectRouterBuilder.class);
            builder.setFile(file);
            builder.setName(projectName);
            injector.getInstance(Engine.class).addHandlerRouter(builder.build());
        } catch (Throwable e) {
            injector.destroy();
            throw e;
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector = Injector.getRoot().createChild(new EngineModule(), MainProxyModule::installProxy);

        try {
            Engine engine = injector.getInstance(Engine.class);
            engine.installExt();

            HttpServer server = injector.getInstance(HttpServer.class);
            server.start(engine);
            server.awaitShutdown();
        } finally {
            injector.destroy();
        }
    }

}
