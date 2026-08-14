package amalitech.hospital.management.enums;

/** Application-level RBAC actions stored in the `permissions.action` column. */
public enum PermissionAction {
    CREATE("create"),
    READ("read"),
    UPDATE("update"),
    DELETE("delete");

    private final String dbValue;

    PermissionAction(String dbValue) { this.dbValue = dbValue; }

    public String getDbValue() { return dbValue; }

    public static PermissionAction fromDbValue(String value) {
        for (PermissionAction a : values()) {
            if (a.dbValue.equalsIgnoreCase(value)) return a;
        }
        throw new IllegalArgumentException("Unknown PermissionAction: " + value);
    }

    @Override public String toString() { return dbValue; }
}