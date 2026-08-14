package amalitech.hospital.management.enums;

/** Maps the `referrals.status` CHECK constraint values. */
public enum ReferralStatus {
    PENDING("pending", "Pending"),
    SCHEDULED("scheduled", "Scheduled"),
    COMPLETED("completed", "Completed");

    private final String dbValue;
    private final String label;

    ReferralStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static ReferralStatus fromDbValue(String value) {
        for (ReferralStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ReferralStatus: " + value);
    }

    @Override public String toString() { return dbValue; }
}