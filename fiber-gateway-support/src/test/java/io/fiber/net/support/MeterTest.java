package io.fiber.net.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

public class MeterTest {

    @Test
    public void t() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        Counter counter = Counter.builder("my_counter")
                .tag("aa", "AAA")
                .tag("bb", "BBB")
                .description("my_counter desc")
                .register(registry);

        Timer timer = Timer.builder("my_timer")
                .description("my_timer desc")
                .tag("aa", "AAA")
                .tag("bb", "BBB")
                .register(registry);

//        registry.remove(counter.getId());

        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        counter.increment();
        System.out.println(counter.count());
        System.out.println(counter.count());
        System.out.println(counter.count());
//        Thread.sleep(3000);

        for (int i = 0; i < 1000; i++) {
            timer.record(100 + i, TimeUnit.MILLISECONDS);
        }

        String scrape = registry.scrape(TextFormat.CONTENT_TYPE_004);
        System.out.println(scrape);
    }
}
