package amalitech.hospital.management.enums;

/** Maps the `system_logs.log_level` CHECK constraint values. */
public enum SystemLogLevel {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR");

    private final String dbValue;

    SystemLogLevel(String dbValue) { this.dbValue = dbValue; }

    public String getDbValue() { return dbValue; }

    public static SystemLogLevel fromDbValue(String value) {
        for (SystemLogLevel l : values()) {
            if (l.dbValue.equalsIgnoreCase(value)) return l;
        }
        throw new IllegalArgumentException("Unknown SystemLogLevel: " + value);
    }

    @Override public String toString() { return dbValue; }
}