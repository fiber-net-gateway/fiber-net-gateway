package io.fiber.net.common.ext;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.Server;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Predictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractServer<E> implements Server<E> {
    protected static final Logger log = LoggerFactory.getLogger(AbstractServer.class);

    private static class InterceptorNode<EX> implements Interceptor.Invocation<EX> {
        private final Interceptor<EX> interceptor;
        private Interceptor.Invocation<EX> next;

        public InterceptorNode(Interceptor<EX> interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void invoke(RouterHandler<EX> handler, EX exchange) throws Exception {
            interceptor.intercept(handler, exchange, next);
        }
    }

    private class FindProjectInvocation implements Interceptor.Invocation<E> {
        @Override
        public void invoke(RouterHandler<E> handler, E exchange) throws Exception {
            handler.invoke(exchange);
        }
    }

    protected Interceptor.Invocation<E> invocation = new FindProjectInvocation();
    private final Map<String, RouterHandler<E>> projectMap = new ConcurrentHashMap<>();
    private final Router<E> router;
    private final ErrorHandler<E> errorHandler;
    private InterceptorNode<E> tail;
    protected final Injector injector;

    @SuppressWarnings("unchecked")
    public AbstractServer(Injector injector) {
        this.injector = injector;
        this.router = injector.getInstance(Router.class);
        this.errorHandler = injector.getInstance(ErrorHandler.class);
        Interceptor<E>[] interceptors = injector.getInstances(Interceptor.class);
        if (ArrayUtils.isNotEmpty(interceptors)) {
            addInterceptors(interceptors);
        }
    }

    public AbstractServer(Injector injector, Router<E> router, ErrorHandler<E> errorHandler) {
        this.injector = injector;
        this.router = router;
        this.errorHandler = errorHandler;
    }

    public void addInterceptor(Interceptor<E> interceptor) {
        Predictions.notNull(interceptor, "interceptor must not null");
        InterceptorNode<E> node = new InterceptorNode<>(interceptor);
        if (tail != null) {
            node.next = tail.next;
            tail.next = node;
        } else {
            node.next = invocation;
            invocation = node;
        }
        this.tail = node;
    }

    @SafeVarargs
    public final void addInterceptors(Interceptor<E>... interceptor) {
        for (Interceptor<E> ipr : interceptor) {
            addInterceptor(ipr);
        }
    }

    @Override
    public final void process(E exchange) {
        try {
            RouterHandler<E> handler = router.route(this, exchange);
            if (handler == null) {
                throw new FiberException("router handler not found", 404, "HANDLER_NOT_FOUND");
            }
            invocation.invoke(handler, exchange);
        } catch (Throwable e) {
            errorHandler.handleErr(exchange, e);
        }
    }

    public void addHandlerRouter(RouterHandler<E> handlerRouter) {
        String managerName = handlerRouter.getRouterName();
        RouterHandler<E> old = projectMap.put(managerName, handlerRouter);
        if (old != null) {
            old.destroy();
            log.info("project {} of server {} is replaced", managerName, getName());
        } else {
            log.info("project {} of server {}  added", managerName, getName());
        }
    }

    public void removeHandlerRouter(String projectName) {
        RouterHandler<E> handlerRouter = projectMap.remove(projectName);
        if (handlerRouter != null) {
            handlerRouter.destroy();
            log.info("project {} of server {} is removed", projectName, getName());
        }
    }

    public RouterHandler<E> getHandlerRouter(String projectName) {
        return projectMap.get(projectName);
    }

    public ErrorHandler<E> getErrorHandler() {
        return errorHandler;
    }

    public Router<E> getRouter() {
        return router;
    }

    public final Injector getInjector() {
        return injector;
    }

    @Override
    public void destroy() {
        for (Map.Entry<String, RouterHandler<E>> entry : projectMap.entrySet()) {
            entry.getValue().destroy();
            log.info("project {} of server {} is removed", entry.getKey(), getName());
        }
        projectMap.clear();
    }
}
