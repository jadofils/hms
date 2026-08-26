package amalitech.hospital.management.enums;

/** Canonical role names stored in `roles.role_name` and seeded by hospital_rbac_seed_postgresql.sql. */
public enum RoleName {
    ADMIN("Admin"),
    DOCTOR("Doctor"),
    RECEPTIONIST("Receptionist"),
    ANALYST("Analyst"),
    PHARMACIST("Pharmacist"),
    /** Auto-granted default for a brand-new self-registered/Google-OAuth2 account not
     *  pre-authorized by an admin invite — see {@code UserService.assignDefaultGuestRole}.
     *  Read-only access to the doctor directory/availability only (see DataSeeder's own
     *  grant list) — never a role an admin assigns by hand. */
    GUEST("Guest");

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