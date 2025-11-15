package io.fiber.net.example.injector;

public class GreetingFacade {

    private final GreetingService service;

    public GreetingFacade(GreetingService service) {
        this.service = service;
    }

    public String greet(String name) {
        return service.greet(name);
    }
}
