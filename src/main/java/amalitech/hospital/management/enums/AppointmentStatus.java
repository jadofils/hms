package amalitech.hospital.management.enums;

/** Maps the `appointments.status` CHECK constraint values. */
public enum AppointmentStatus {
    SCHEDULED("scheduled", "Scheduled"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled");

    private final String dbValue;
    private final String label;

    AppointmentStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static AppointmentStatus fromDbValue(String value) {
        for (AppointmentStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown AppointmentStatus: " + value);
    }

    @Override public String toString() { return dbValue; }
}