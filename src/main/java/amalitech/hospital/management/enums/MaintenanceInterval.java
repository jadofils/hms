package amalitech.hospital.management.enums;

/** How often a {@code @ScheduledMaintenance}-annotated method runs — deliberately just
 *  these two units ("days or hours" per the original request), not the full
 *  {@code java.util.concurrent.TimeUnit} surface. */
public enum MaintenanceInterval {
    HOURS,
    DAYS
}
