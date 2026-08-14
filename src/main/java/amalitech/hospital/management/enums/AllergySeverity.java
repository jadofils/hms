package amalitech.hospital.management.enums;

/** Maps the `patient_allergies.severity` CHECK constraint values. */
public enum AllergySeverity {
    MILD("mild", "Mild"),
    MODERATE("moderate", "Moderate"),
    SEVERE("severe", "Severe");

    private final String dbValue;
    private final String label;

    AllergySeverity(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static AllergySeverity fromDbValue(String value) {
        for (AllergySeverity s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown AllergySeverity: " + value);
    }

    @Override public String toString() { return dbValue; }
}