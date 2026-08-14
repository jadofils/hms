package amalitech.hospital.management.enums;

/** Maps the `invoices.payment_status` CHECK constraint values. */
public enum PaymentStatus {
    UNPAID("unpaid", "Unpaid"),
    PARTIALLY_PAID("partially_paid", "Partially Paid"),
    PAID("paid", "Paid");

    private final String dbValue;
    private final String label;

    PaymentStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static PaymentStatus fromDbValue(String value) {
        for (PaymentStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown PaymentStatus: " + value);
    }

    @Override public String toString() { return dbValue; }
}