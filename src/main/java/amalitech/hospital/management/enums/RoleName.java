package amalitech.hospital.management.enums;

/** Canonical role names stored in `roles.role_name` and seeded by hospital_rbac_seed_postgresql.sql. */
public enum RoleName {
    ADMIN("Admin"),
    DOCTOR("Doctor"),
    RECEPTIONIST("Receptionist"),
    ANALYST("Analyst"),
    PHARMACIST("Pharmacist");

    private final String dbValue;

    RoleName(String dbValue) { this.dbValue = dbValue; }

    public String getDbValue() { return dbValue; }

    public static RoleName fromDbValue(String value) {
        for (RoleName r : values()) {
            if (r.dbValue.equalsIgnoreCase(value)) return r;
        }
        throw new IllegalArgumentException("Unknown RoleName: " + value);
    }

    @Override public String toString() { return dbValue; }
}