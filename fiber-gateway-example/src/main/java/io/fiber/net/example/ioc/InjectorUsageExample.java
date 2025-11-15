package io.fiber.net.example.ioc;

import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.ioc.Module;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Small usage sample showcasing the Injector API: child injectors, forking, multi-bindings, and Destroyable lifecycles.
 */
public final class InjectorUsageExample {

    private InjectorUsageExample() {
    }

    public static void main(String[] args) {
        ExampleApplication application = bootstrap("fiber-net-gateway");
        try {
            ExampleResponse response = application.handleRequest("developer");
            System.out.println("RequestId=" + response.getRequestId());
            System.out.println("Summary=" + response.getMessage());
            response.getPluginOutputs().forEach(line -> System.out.println("  - " + line));
        } finally {
            application.destroy();
        }
    }

    public static ExampleApplication bootstrap(String projectName) {
        Injector engineInjector = Injector.getRoot().createChild(new EngineModule(projectName));
        Collection<Module> modules = Arrays.asList(
                new GreetingModule(),
                new PluginModule(),
                new LifecycleModule()
        );
        Injector projectInjector = engineInjector.createChild(modules);
        ExampleApplication application = new ExampleApplication(engineInjector, projectInjector);
        application.preWarmHandlers();
        return application;
    }

    public static ExampleApplication bootstrap() {
        return bootstrap("fiber-net-gateway");
    }

    public static final class ExampleApplication implements Destroyable {
        private final Injector engineInjector;
        private final Injector projectInjector;
        private final LifecycleRecorder lifecycleRecorder;

        private ExampleApplication(Injector engineInjector, Injector projectInjector) {
            this.engineInjector = engineInjector;
            this.projectInjector = projectInjector;
            this.lifecycleRecorder = projectInjector.getInstance(LifecycleRecorder.class);
            projectInjector.getInstance(LifecycleAwareResource.class);
        }

        public LifecycleRecorder getLifecycleRecorder() {
            return lifecycleRecorder;
        }

        public void preWarmHandlers() {
            Injector warmup = projectInjector.fork();
            try {
                warmup.getInstance(GreetingWorkflow.class);
                warmup.getInstances(GreetingHandler.class);
            } finally {
                warmup.destroy();
            }
        }

        public ExampleResponse handleRequest(String userName) {
            Injector requestInjector = projectInjector.createChild(new RequestScopeModule());
            try {
                RequestContext context = requestInjector.getInstance(RequestContext.class);
                GreetingWorkflow workflow = requestInjector.getInstance(GreetingWorkflow.class);
                GreetingHandler[] handlers = requestInjector.getInstances(GreetingHandler.class);
                return workflow.handle(userName, context, handlers);
            } finally {
                requestInjector.destroy();
            }
        }

        @Override
        public void destroy() {
            projectInjector.destroy();
            engineInjector.destroy();
        }
    }

    private static final class EngineModule implements Module {
        private final String projectName;

        private EngineModule(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void install(Binder binder) {
            binder.bind(AppConfig.class, new AppConfig(projectName, "dev"));
        }
    }

    private static final class GreetingModule implements Module {
        @Override
        public void install(Binder binder) {
            binder.bindFactory(MessageFormatter.class, injector -> new MessageFormatter());
            binder.bindFactory(GreetingWorkflow.class, injector ->
                    new GreetingWorkflow(
                            injector.getInstance(AppConfig.class),
                            injector.getInstance(MessageFormatter.class),
                            injector.getInstance(LifecycleAwareResource.class)
                    )
            );
        }
    }

    private static final class PluginModule implements Module {
        @Override
        public void install(Binder binder) {
            binder.bindMultiBean(GreetingHandler.class, new FriendlyGreetingHandler(), 0);
            binder.bindMultiBeanPrototype(GreetingHandler.class,
                    injector -> new TimedGreetingHandler(injector.getInstance(AppConfig.class)), 5);
            binder.bindMultiBean(GreetingHandler.class, new UppercaseGreetingHandler(), 10);
        }
    }

    private static final class LifecycleModule implements Module {
        @Override
        public void install(Binder binder) {
            binder.bind(LifecycleRecorder.class, new LifecycleRecorder());
            binder.bindFactory(LifecycleAwareResource.class,
                    injector -> injector.getInstance(LifecycleRecorder.class).createResource());
        }
    }

    /**
     * Child injector showing request scoped objects layered on top of project-level singletons.
     */
    private static final class RequestScopeModule implements Module {
        @Override
        public void install(Binder binder) {
            binder.bindPrototype(RequestContext.class, injector -> RequestContext.create());
        }
    }

    static final class ExampleResponse {
        private final String requestId;
        private final String message;
        private final List<String> pluginOutputs;

        ExampleResponse(String requestId, String message, List<String> pluginOutputs) {
            this.requestId = requestId;
            this.message = message;
            this.pluginOutputs = Collections.unmodifiableList(new ArrayList<>(pluginOutputs));
        }

        public String getRequestId() {
            return requestId;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getPluginOutputs() {
            return pluginOutputs;
        }
    }

    private static final class AppConfig {
        private final String projectName;
        private final String environment;

        private AppConfig(String projectName, String environment) {
            this.projectName = projectName;
            this.environment = environment;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getEnvironment() {
            return environment;
        }
    }

    private interface GreetingHandler {
        String handle(String userName, RequestContext context);
    }

    private static final class GreetingWorkflow {
        private final AppConfig config;
        private final MessageFormatter formatter;
        private final LifecycleAwareResource lifecycleAwareResource;

        private GreetingWorkflow(AppConfig config,
                                 MessageFormatter formatter,
                                 LifecycleAwareResource lifecycleAwareResource) {
            this.config = config;
            this.formatter = formatter;
            this.lifecycleAwareResource = lifecycleAwareResource;
        }

        private ExampleResponse handle(String userName, RequestContext context, GreetingHandler[] handlers) {
            List<String> messages = new ArrayList<>(handlers.length);
            for (GreetingHandler handler : handlers) {
                messages.add(handler.handle(userName, context));
            }
            String summary = formatter.format(config, lifecycleAwareResource, context, handlers.length, messages);
            return new ExampleResponse(context.getRequestId(), summary, messages);
        }
    }

    private static final class MessageFormatter {
        private String format(AppConfig config,
                              LifecycleAwareResource resource,
                              RequestContext context,
                              int handlerCount,
                              List<String> outputs) {
            return config.getProjectName()
                    + "/" + config.getEnvironment()
                    + " served by " + resource.describe()
                    + " for request " + context.getRequestId()
                    + " using " + handlerCount + " handlers -> " + outputs.size() + " lines";
        }
    }

    private static final class FriendlyGreetingHandler implements GreetingHandler {
        @Override
        public String handle(String userName, RequestContext context) {
            return "Friendly hello " + userName + " (" + context.compactRequestId() + ")";
        }
    }

    private static final class TimedGreetingHandler implements GreetingHandler {
        private static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        private final AppConfig config;

        private TimedGreetingHandler(AppConfig config) {
            this.config = config;
        }

        @Override
        public String handle(String userName, RequestContext context) {
            return "Timed greeting for " + userName + " on " + config.getEnvironment()
                    + " at " + FORMATTER.format(context.getCreatedAt());
        }
    }

    private static final class UppercaseGreetingHandler implements GreetingHandler {
        @Override
        public String handle(String userName, RequestContext context) {
            return ("LOUD " + userName + "@" + context.compactRequestId()).toUpperCase();
        }
    }

    private static final class RequestContext {
        private final String requestId;
        private final Instant createdAt;

        private RequestContext(String requestId, Instant createdAt) {
            this.requestId = requestId;
            this.createdAt = createdAt;
        }

        private static RequestContext create() {
            return new RequestContext(UUID.randomUUID().toString(), Instant.now());
        }

        public String getRequestId() {
            return requestId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        String compactRequestId() {
            return requestId.substring(0, 8);
        }
    }

    static final class LifecycleRecorder {
        private final java.util.concurrent.atomic.AtomicInteger createdCounter = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger destroyedCounter = new java.util.concurrent.atomic.AtomicInteger();

        LifecycleAwareResource createResource() {
            int id = createdCounter.incrementAndGet();
            return new LifecycleAwareResource(this, id);
        }

        void markDestroyed() {
            destroyedCounter.incrementAndGet();
        }

        int createdCount() {
            return createdCounter.get();
        }

        int destroyedCount() {
            return destroyedCounter.get();
        }
    }

    private static final class LifecycleAwareResource implements Destroyable {
        private final LifecycleRecorder recorder;
        private final int id;

        private LifecycleAwareResource(LifecycleRecorder recorder, int id) {
            this.recorder = recorder;
            this.id = id;
        }

        String describe() {
            return "lifecycle#" + id;
        }

        @Override
        public void destroy() {
            recorder.markDestroyed();
        }
    }
}
