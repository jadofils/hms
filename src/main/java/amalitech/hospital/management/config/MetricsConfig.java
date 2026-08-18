package amalitech.hospital.management.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link TimedAspect} — the one bean Micrometer's {@code @Timed} annotation
 * needs to actually do anything. Spring Boot's actuator autoconfiguration gives every
 * app a {@link MeterRegistry} automatically, but does <em>not</em> register this aspect
 * on its own; without it, every {@code @Timed} on a controller/resolver in this codebase
 * would just be an inert annotation nobody reads.
 *
 * <p>See {@code docs/performance-report.md}'s "Live metrics" section for what this feeds:
 * {@code @Timed("hms.rest.requests")}/{@code @Timed("hms.graphql.requests")} on every
 * REST controller/GraphQL resolver give the one-off {@code RestVsGraphQlBenchmarkTest}
 * comparison a live, continuously-collected counterpart, queryable at
 * {@code /actuator/metrics/hms.rest.requests}/{@code hms.graphql.requests} or scraped via
 * {@code /actuator/prometheus}.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }
}
