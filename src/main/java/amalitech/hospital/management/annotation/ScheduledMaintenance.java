package amalitech.hospital.management.annotation;

import amalitech.hospital.management.enums.MaintenanceInterval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a periodic background maintenance task — intercepted not by an
 * {@code @Around}/{@code @Before} aspect (those only fire when *something else* calls
 * the annotated method) but by {@code ScheduledMaintenanceRegistrar}, a
 * {@code BeanPostProcessor} that registers each one with a real {@code TaskScheduler}
 * at startup. See CLAUDE.md's AOP conventions section for why this project always
 * routes a mechanism like this through a dedicated annotation rather than calling the
 * underlying utility directly.
 *
 * {@code interval}/{@code unit} control run *frequency* only — how often the method
 * fires. Any retention/idle-threshold value the method itself uses internally (e.g.
 * "how old counts as stale") is a separate, independently configurable concern — see
 * {@code MaintenanceService}'s own {@code @Value}-injected fields.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledMaintenance {

    /** A short, stable identifier for this task (e.g. {@code "log-cleanup"}) — used in
     *  log lines so a given run can be traced back to which annotation fired it. */
    String value();

    long interval();

    MaintenanceInterval unit();
}
