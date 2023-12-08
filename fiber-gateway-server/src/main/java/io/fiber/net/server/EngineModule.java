package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.ext.RouterNameFetcher;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Initializable;
import io.fiber.net.common.ioc.Module;
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
        binder.bindFactory(Engine.class, Engine::new);
        binder.bind(RouterNameFetcher.class, httpExchange -> {
            String header = httpExchange.getRequestHeader("X-Fiber-Project");
            if(StringUtils.isEmpty(header)){
                return RouterNameFetcher.DEF_ROUTER_NAME;
            }
            return header;
        });
        binder.bindFactory(EventLoopGroupHolder.class, injector -> new EventLoopGroupHolder());
        binder.bindPrototype(EventLoopGroup.class, injector -> injector.getInstance(EventLoopGroupHolder.class).getGroup());
        binder.bindFactory(HttpServer.class, injector -> new Server(injector.getInstance(EventLoopGroup.class)));
    }
}
