package amalitech.hospital.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Supplies the {@link TaskScheduler} {@code ScheduledMaintenanceRegistrar} registers
 * every {@code @ScheduledMaintenance} method against. Deliberately not
 * {@code @EnableScheduling} + Spring's own {@code @Scheduled} — that mechanism has no
 * per-method compile-time-checked annotation attributes of its own; this project always
 * routes a "run this periodically" need through a dedicated custom annotation instead
 * (see CLAUDE.md's AOP conventions section).
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("hms-maintenance-");
        scheduler.initialize();
        return scheduler;
    }
}
