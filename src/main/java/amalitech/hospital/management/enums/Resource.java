package amalitech.hospital.management.enums;

/**
 * Every resource the RBAC permission catalog covers — mirrors the {@code resource}
 * column of {@code permissions} and drives both {@link amalitech.hospital.management.config.DataSeeder}'s
 * seeded catalog and every {@code @RequirePermission} annotation, so a typo'd resource
 * string can't silently deny everyone forever with no compile-time signal.
 *
 * {@code Permission.resource} itself stays a plain DB {@code String} column — an admin
 * can still create an ad hoc permission for a resource with no matching constant here via
 * the Permission CRUD API; it just won't be enforceable by {@code @RequirePermission}
 * until a constant (and an annotation using it) exists.
 */
public enum Resource {
    USERS("users", "Users"),
    ROLES("roles", "Roles"),
    PERMISSIONS("permissions", "Permissions"),
    PATIENTS("patients", "Patients"),
    DOCTORS("doctors", "Doctors"),
    DEPARTMENTS("departments", "Departments"),
    DOCTOR_SCHEDULES("doctor-schedules", "Doctor Schedules"),
    APPOINTMENTS("appointments", "Appointments"),
    MEDICATIONS("medications", "Medications"),
    MEDICAL_INVENTORY("medical-inventory", "Medical Inventory"),
    PRESCRIPTIONS("prescriptions", "Prescriptions"),
    PRESCRIPTION_ITEMS("prescription-items", "Prescription Items"),
    LAB_ORDERS("lab-orders", "Lab Orders"),
    LAB_RESULTS("lab-results", "Lab Results"),
    INVOICES("invoices", "Invoices"),
    NOTIFICATIONS("notifications", "Notifications"),
    EVENTS("events", "Events"),
    SYSTEM_LOGS("system-logs", "System Logs");

    private final String dbValue;
    private final String label;

    Resource(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel() { return label; }

    public static Resource fromDbValue(String value) {
        for (Resource r : values()) {
            if (r.dbValue.equalsIgnoreCase(value)) return r;
        }
        throw new IllegalArgumentException("Unknown Resource: " + value);
    }

    @Override public String toString() { return dbValue; }
}
