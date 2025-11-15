package io.fiber.net.example.injector;

import io.fiber.net.common.ioc.Injector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class InjectorUsageTest {

    private Injector injector;

    @Before
    public void setUp() {
        injector = InjectorUsageExample.createGreetingInjector();
    }

    @After
    public void tearDown() {
        if (injector != null) {
            injector.destroy();
        }
    }

    @Test
    public void shouldInjectGreetingFacade() {
        GreetingFacade facade = injector.getInstance(GreetingFacade.class);
        assertNotNull(facade);
        assertEquals("Hello, Fiber!", facade.greet("Fiber"));
    }

    @Test
    public void greetHelperShouldDelegateToFacade() {
        String greeting = InjectorUsageExample.greet(injector, "Gateway");
        assertEquals("Hello, Gateway!", greeting);
    }
}
