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

public class Engine implements Destroyable {
    private static final Logger log = LoggerFactory.getLogger(Engine.class);


    private static class InterceptorNode implements Interceptor.Invocation {
        private final Interceptor interceptor;
        private Interceptor.Invocation next;

        public InterceptorNode(Interceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void invoke(String project, HttpExchange httpExchange) throws Exception {
            interceptor.intercept(project, httpExchange, next);
        }
    }

    private class FindProjectInvocation implements Interceptor.Invocation {
        @Override
        public void invoke(String project, HttpExchange httpExchange) throws Exception {
            runInternal(project, httpExchange);
        }
    }

    protected Interceptor.Invocation invocation = new FindProjectInvocation();
    private final Map<String, RequestHandlerRouter> projectMap = new ConcurrentHashMap<>();
    private InterceptorNode tail;
    private final Injector injector;
    private RouterNameFetcher routerNameFetcher = RouterNameFetcher.DEF_NAME;

    public Engine(Injector injector) {
        this.injector = injector;
    }

    public void installExt() throws Exception {
        routerNameFetcher = injector.getInstance(RouterNameFetcher.class);
        Interceptor[] interceptors = injector.getInstances(Interceptor.class);
        if (ArrayUtils.isNotEmpty(interceptors)) {
            addInterceptors(interceptors);
        }
        StartListener[] startListeners = injector.getInstances(StartListener.class);
        if (ArrayUtils.isNotEmpty(startListeners)) {
            for (StartListener startListener : startListeners) {
                startListener.onStart(this);
            }
        }
    }

    public void addInterceptor(Interceptor interceptor) {
        Predictions.notNull(interceptor, "interceptor must not null");
        InterceptorNode node = new InterceptorNode(interceptor);
        if (tail != null) {
            node.next = tail.next;
            tail.next = node;
        } else {
            node.next = invocation;
            invocation = node;
        }
        Predictions.assertTrue(node.next instanceof FindProjectInvocation, "last is not FindProjectInvocation??");
        this.tail = node;
    }

    public final void addInterceptors(Interceptor... interceptor) {
        for (Interceptor ipr : interceptor) {
            addInterceptor(ipr);
        }
    }

    public final void run(String projectName, HttpExchange httpExchange) throws Exception {
        invocation.invoke(projectName, httpExchange);
    }

    public void run(HttpExchange httpExchange) throws Exception {
        run(routerNameFetcher.fetchName(httpExchange), httpExchange);
    }

    public void addHandlerRouter(RequestHandlerRouter handlerRouter) {
        String managerName = handlerRouter.getRouterName();
        RequestHandlerRouter old = projectMap.put(managerName, handlerRouter);
        if (old != null) {
            old.destroy();
            log.info("project {} is replaced", managerName);
        } else {
            log.info("project {} is added", managerName);
        }
    }

    public void removeHandlerRouter(String projectName) {
        RequestHandlerRouter handlerRouter = projectMap.remove(projectName);
        if (handlerRouter != null) {
            handlerRouter.destroy();
            log.info("project {} is removed", projectName);
        }
    }

    protected void runInternal(String project, HttpExchange httpExchange) throws Exception {
        RequestHandlerRouter handlerManager = getHandlerRouter(project);
        if (handlerManager == null) {
            httpExchange.discardReqBody();
            httpExchange.writeJson(404, "NOT_FOUND");
            return;
        }
        handlerManager.invoke(httpExchange);
    }

    public RequestHandlerRouter getHandlerRouter(String projectName) {
        return projectMap.get(projectName);
    }

    public final Injector getInjector() {
        return injector;
    }

    @Override
    public void destroy() {
        for (Map.Entry<String, RequestHandlerRouter> entry : projectMap.entrySet()) {
            entry.getValue().destroy();
            log.info("project {} is removed", entry.getKey());
        }
        projectMap.clear();
    }
}
