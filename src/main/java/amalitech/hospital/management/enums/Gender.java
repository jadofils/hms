package amalitech.hospital.management.enums;

/** Maps the `patients.gender` CHECK constraint values. */
public enum Gender {
    M("M", "Male"),
    F("F", "Female"),
    OTHER("Other", "Other");

    private final String dbValue;
    private final String label;

    Gender(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static Gender fromDbValue(String value) {
        for (Gender g : values()) {
            if (g.dbValue.equalsIgnoreCase(value)) return g;
        }
        throw new IllegalArgumentException("Unknown Gender: " + value);
    }

    /** Finds by display label, e.g. "Male" → M. Used when reading from UI ComboBox. */
    public static Gender fromLabel(String label) {
        for (Gender g : values()) {
            if (g.label.equalsIgnoreCase(label)) return g;
        }
        throw new IllegalArgumentException("Unknown Gender label: " + label);
    }

    @Override public String toString() { return dbValue; }
}