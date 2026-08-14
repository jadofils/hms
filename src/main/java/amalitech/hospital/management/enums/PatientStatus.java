package amalitech.hospital.management.enums;

/** Maps the `patients.status` CHECK constraint values. */
public enum PatientStatus {
    ACTIVE("active", "Active"),
    INACTIVE("inactive", "Inactive");

    private final String dbValue;
    private final String label;

    PatientStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static PatientStatus fromDbValue(String value) {
        for (PatientStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown PatientStatus: " + value);
    }

    @Override public String toString() { return dbValue; }
}
