package io.fiber.net.example.injector;

public class GreetingServiceImpl implements GreetingService {

    private final String prefix;

    public GreetingServiceImpl() {
        this("Hello");
    }

    public GreetingServiceImpl(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String greet(String name) {
        return prefix + ", " + name + "!";
    }
}
