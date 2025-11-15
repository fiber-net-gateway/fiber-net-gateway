package io.fiber.net.example.ioc;

import io.fiber.net.example.ioc.InjectorUsageExample.ExampleApplication;
import io.fiber.net.example.ioc.InjectorUsageExample.ExampleResponse;
import io.fiber.net.example.ioc.InjectorUsageExample.LifecycleRecorder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InjectorUsageExampleTest {

    @Test
    public void shouldHandleRequestWithParentChildInjectors() {
        ExampleApplication application = InjectorUsageExample.bootstrap("demo-project");
        try {
            ExampleResponse first = application.handleRequest("Agent");
            ExampleResponse second = application.handleRequest("Agent");
            assertNotNull(first.getRequestId());
            assertNotNull(first.getMessage());
            assertEquals(3, first.getPluginOutputs().size());
            assertNotEquals(first.getRequestId(), second.getRequestId());
            assertTrue(first.getMessage().contains("demo-project"));
        } finally {
            application.destroy();
        }
    }

    @Test
    public void shouldTrackDestroyableLifecycleAcrossForks() {
        ExampleApplication application = InjectorUsageExample.bootstrap("demo");
        LifecycleRecorder recorder = application.getLifecycleRecorder();
        try {
            assertEquals("main + warmup instance created", 2, recorder.createdCount());
            assertEquals("warmup injector destroyed", 1, recorder.destroyedCount());
            application.handleRequest("tester");
        } finally {
            application.destroy();
        }
        assertEquals(2, recorder.destroyedCount());
    }
}
