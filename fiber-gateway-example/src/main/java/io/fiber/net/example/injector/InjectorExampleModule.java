package io.fiber.net.example.injector;

import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Module;

public class InjectorExampleModule implements Module {
    @Override
    public void install(Binder binder) {
        binder.bindFactory(GreetingService.class, injector -> new GreetingServiceImpl());
        binder.bindFactory(GreetingFacade.class, injector -> new GreetingFacade(injector.getInstance(GreetingService.class)));
    }
}
