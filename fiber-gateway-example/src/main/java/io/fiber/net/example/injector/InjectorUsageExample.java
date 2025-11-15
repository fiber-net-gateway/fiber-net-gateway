package io.fiber.net.example.injector;

import io.fiber.net.common.ioc.Injector;

import java.util.Collections;

public final class InjectorUsageExample {

    private InjectorUsageExample() {
    }

    public static Injector createGreetingInjector() {
        return Injector.getRoot().createChild(Collections.singletonList(new InjectorExampleModule()));
    }

    public static String greet(Injector injector, String name) {
        GreetingFacade facade = injector.getInstance(GreetingFacade.class);
        return facade.greet(name);
    }
}
