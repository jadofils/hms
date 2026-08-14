package amalitech.hospital.management.enums;

/** Maps the `lab_orders.status` CHECK constraint values. */
public enum LabOrderStatus {
    ORDERED("ordered", "Ordered"),
    IN_PROGRESS("in_progress", "In Progress"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled");

    private final String dbValue;
    private final String label;

    LabOrderStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static LabOrderStatus fromDbValue(String value) {
        for (LabOrderStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown LabOrderStatus: " + value);
    }

    @Override public String toString() { return dbValue; }
}