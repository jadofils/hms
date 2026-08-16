package amalitech.hospital.management.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a listener for a domain event — see
 * {@code amalitech.hospital.management.aop.EventBus}, which scans every bean for
 * {@code @Subscribe} methods at startup and dispatches each published event to every
 * currently-enabled subscriber whose {@link #event()} matches. {@link #name()} is the
 * stable id an admin toggles on/off at runtime via
 * {@code EventSubscriptionController}'s subscribe/unsubscribe endpoints — it must be
 * unique across the whole application, the same way a {@code @ScheduledMaintenance}
 * task's {@code value()} does.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    String name();
    Class<?> event();
}
