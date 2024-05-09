package io.fiber.net.common;

import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.common.ext.RouterNameFetcher;
import io.fiber.net.common.ext.StartListener;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Predictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Engine<E> implements Destroyable {
    private static final Logger log = LoggerFactory.getLogger(Engine.class);


    private static class InterceptorNode<EX> implements Interceptor.Invocation<EX> {
        private final Interceptor<EX> interceptor;
        private Interceptor.Invocation<EX> next;

        public InterceptorNode(Interceptor<EX> interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void invoke(String project, EX exchange) throws Exception {
            interceptor.intercept(project, exchange, next);
        }
    }

    private class FindProjectInvocation implements Interceptor.Invocation<E> {
        @Override
        public void invoke(String project, E exchange) throws Exception {
            runInternal(getHandlerRouter(project), exchange);
        }
    }

    protected Interceptor.Invocation<E> invocation = new FindProjectInvocation();
    private final Map<String, RouterHandler<E>> projectMap = new ConcurrentHashMap<>();
    private InterceptorNode<E> tail;
    private final Injector injector;
    private RouterNameFetcher<E> routerNameFetcher = exchange -> RouterNameFetcher.DEF_ROUTER_NAME;

    public Engine(Injector injector) {
        this.injector = injector;
    }

    @SuppressWarnings("unchecked")
    public void installExt() throws Exception {
        routerNameFetcher = injector.getInstance(RouterNameFetcher.class);
        Interceptor<E>[] interceptors = injector.getInstances(Interceptor.class);
        if (ArrayUtils.isNotEmpty(interceptors)) {
            addInterceptors(interceptors);
        }
        StartListener<E, Engine<E>>[] startListeners = injector.getInstances(StartListener.class);
        if (ArrayUtils.isNotEmpty(startListeners)) {
            for (StartListener<E, Engine<E>> startListener : startListeners) {
                startListener.onStart(this);
            }
        }
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

    public final void run(String projectName, E exchange) throws Exception {
        invocation.invoke(projectName, exchange);
    }

    public void run(E exchange) throws Exception {
        run(routerNameFetcher.fetchName(exchange), exchange);
    }

    public void addHandlerRouter(RouterHandler<E> handlerRouter) {
        String managerName = handlerRouter.getRouterName();
        RouterHandler<E> old = projectMap.put(managerName, handlerRouter);
        if (old != null) {
            old.destroy();
            log.info("project {} is replaced", managerName);
        } else {
            log.info("project {} is added", managerName);
        }
    }

    public void removeHandlerRouter(String projectName) {
        RouterHandler<E> handlerRouter = projectMap.remove(projectName);
        if (handlerRouter != null) {
            handlerRouter.destroy();
            log.info("project {} is removed", projectName);
        }
    }

    protected void runInternal(RouterHandler<E> router, E exchange) throws Exception {
        if (router == null) {
            return;
        }
        router.invoke(exchange);
    }

    public RouterHandler<E> getHandlerRouter(String projectName) {
        return projectMap.get(projectName);
    }

    public final Injector getInjector() {
        return injector;
    }

    @Override
    public void destroy() {
        for (Map.Entry<String, RouterHandler<E>> entry : projectMap.entrySet()) {
            entry.getValue().destroy();
            log.info("project {} is removed", entry.getKey());
        }
        projectMap.clear();
    }
}
