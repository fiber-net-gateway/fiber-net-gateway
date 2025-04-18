package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ext.RouterNameFetcher;
import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Initializable;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.StringUtils;
import io.netty.channel.EventLoopGroup;

public class EngineModule implements Module {

    public static class EventLoopGroupHolder implements Destroyable, Initializable {
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
        binder.bindFactory(Engine.class, HttpEngine::new);
        binder.bind(RouterNameFetcher.class, (RouterNameFetcher<HttpExchange>) exchange -> {
            String header = exchange.getRequestHeader(Constant.X_FIBER_PROJECT_HEADER);
            if (StringUtils.isEmpty(header)) {
                return RouterNameFetcher.DEF_ROUTER_NAME;
            }
            return header;
        });
        binder.bind(ErrorHandler.class, HttpEngine.ERR_HANDLER);
        binder.bindFactory(EventLoopGroupHolder.class, injector -> new EventLoopGroupHolder());
        binder.bindPrototype(EventLoopGroup.class, injector -> injector.getInstance(EventLoopGroupHolder.class).getGroup());
        binder.bindFactory(HttpServer.class, injector -> new Server(injector.getInstance(EventLoopGroup.class)));
        binder.bindMultiBean(StartListener.class, HttpServerStartListener.class);
        if (!binder.contains(HttpServerStartListener.class)) {
            binder.bindPrototype(HttpServerStartListener.class, HttpServerStartListener::new);
        }
    }
}
