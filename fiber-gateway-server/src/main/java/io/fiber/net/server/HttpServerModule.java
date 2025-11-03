package io.fiber.net.server;

import io.fiber.net.common.Server;
import io.fiber.net.common.ext.ErrorHandler;
import io.fiber.net.common.ext.Router;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.StringUtils;
import io.netty.channel.EventLoopGroup;

public class HttpServerModule implements Module {

    @Override
    public void install(Binder binder) {
        binder.bindMultiBean(ServerModule.class, new SubModule());
        binder.bindFactory(Factory.class, Factory::new);
        binder.bindMultiBeanPrototype(Server.class, i -> createHttpServer(i, "main", new ServerConfig()));
    }

    public static class Factory {
        private final Injector engineInjector;
        private volatile Injector serverInjector;

        private Factory(Injector engineInjector) {
            this.engineInjector = engineInjector;
        }

        private Injector createServerInjector() {
            if (serverInjector == null) {
                synchronized (this) {
                    if (serverInjector == null) {
                        return serverInjector = engineInjector.createChild(engineInjector.getInstances(ServerModule.class));
                    }
                }
            }
            return serverInjector.fork();
        }

        public HttpServer createHttpServer(String name, ServerConfig config) {
            Injector injector = createServerInjector();
            return new HttpServer(injector, name, config);
        }

    }

    public static HttpServer createHttpServer(Injector engineInjector, String name, ServerConfig config) {
        return engineInjector.getInstance(Factory.class).createHttpServer(name, config);
    }

    private static class BossGroupHolder implements Destroyable {
        private final EventLoopGroup group = EpollAvailable.bossGroup();

        public EventLoopGroup getGroup() {
            return group;
        }

        @Override
        public void destroy() {
            group.shutdownGracefully().awaitUninterruptibly();
        }
    }

    private static class SubModule implements ServerModule {

        @Override
        public void install(Binder binder) {
            binder.bind(Router.class, (Router<HttpExchange>) (server, exchange) -> {
                String header = exchange.getRequestHeader(Constant.X_FIBER_PROJECT_HEADER);
                if (StringUtils.isEmpty(header)) {
                    header = Constant.FIBER_NET;
                }
                return server.getHandlerRouter(header);
            });
            binder.bind(ErrorHandler.class, HttpServer.ERR_HANDLER);
            binder.bindFactory(BossGroupHolder.class, i -> new BossGroupHolder());
            binder.bindPrototype(EventLoopGroup.class, injector -> injector.getInstance(BossGroupHolder.class).getGroup());
        }
    }
}
