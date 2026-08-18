package amalitech.hospital.management.config;

import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.enums.RoleName;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import amalitech.hospital.management.service.RoleService;
import amalitech.hospital.management.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the RBAC catalog (permissions, roles, role→permission grants) and one sample
 * login per role on every application startup. Idempotent — every step checks whether
 * its row already exists before creating it (and grants/role-assignments that already
 * exist are simply skipped via the same {@link ConflictException} the services already
 * throw for a duplicate), so this is safe to leave running in every environment rather
 * than being a one-off migration.
 *
 * Deliberately goes through {@link RoleService}/{@link PermissionService}/
 * {@link UserService} for every write (not straight repository inserts) so seeded rows
 * get the exact same timestamps/validation/cache behavior as rows created through the
 * API — the repositories here are used only for the read-side existence/id lookups the
 * services don't expose (find-by-name, find-by-resource-and-action).
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Each of these ":read" grants recurs across multiple roles below — named once here
    // rather than repeating the literal so a typo can't silently create a mismatched key.
    private static final String PATIENTS_READ = "patients:read";
    private static final String DOCTORS_READ = "doctors:read";
    private static final String DEPARTMENTS_READ = "departments:read";
    private static final String DOCTOR_SCHEDULES_READ = "doctor-schedules:read";
    private static final String APPOINTMENTS_READ = "appointments:read";
    private static final String MEDICATIONS_READ = "medications:read";
    private static final String MEDICAL_INVENTORY_READ = "medical-inventory:read";
    private static final String PRESCRIPTIONS_READ = "prescriptions:read";
    private static final String PRESCRIPTION_ITEMS_READ = "prescription-items:read";
    private static final String LAB_ORDERS_READ = "lab-orders:read";
    private static final String LAB_RESULTS_READ = "lab-results:read";
    private static final String INVOICES_READ = "invoices:read";
    private static final String NOTIFICATIONS_READ = "notifications:read";
    private static final String NOTIFICATIONS_UPDATE = "notifications:update";

    /** Realistic per-role scoping — Admin gets every permission (handled separately
     *  below); every other role only gets what its real-world job needs. Keys reference
     *  {@link Resource}'s own dbValues, so this stays in sync with whatever
     *  {@code @RequirePermission} annotations actually check. */
    private static final Map<RoleName, List<String>> ROLE_GRANTS = Map.of(
            RoleName.DOCTOR, List.of(
                    PATIENTS_READ, DOCTORS_READ, "doctors:update", DEPARTMENTS_READ,
                    "doctor-schedules:create", DOCTOR_SCHEDULES_READ,
                    "doctor-schedules:update", "doctor-schedules:delete",
                    APPOINTMENTS_READ, "appointments:update",
                    "prescriptions:create", PRESCRIPTIONS_READ, "prescriptions:update",
                    "prescription-items:create", PRESCRIPTION_ITEMS_READ,
                    "prescription-items:update", "prescription-items:delete",
                    "lab-orders:create", LAB_ORDERS_READ, "lab-orders:update",
                    "lab-results:create", LAB_RESULTS_READ, "lab-results:update",
                    NOTIFICATIONS_READ, NOTIFICATIONS_UPDATE),
            RoleName.RECEPTIONIST, List.of(
                    "patients:create", PATIENTS_READ, "patients:update", "patients:delete",
                    DOCTORS_READ, DEPARTMENTS_READ, DOCTOR_SCHEDULES_READ,
                    "appointments:create", APPOINTMENTS_READ, "appointments:update", "appointments:delete",
                    "invoices:create", INVOICES_READ, "invoices:update", "invoices:delete",
                    NOTIFICATIONS_READ, NOTIFICATIONS_UPDATE),
            RoleName.ANALYST, List.of(
                    "users:read", "roles:read", "permissions:read",
                    PATIENTS_READ, DOCTORS_READ, DEPARTMENTS_READ, DOCTOR_SCHEDULES_READ,
                    APPOINTMENTS_READ, MEDICATIONS_READ, MEDICAL_INVENTORY_READ,
                    PRESCRIPTIONS_READ, PRESCRIPTION_ITEMS_READ, LAB_ORDERS_READ,
                    LAB_RESULTS_READ, INVOICES_READ, NOTIFICATIONS_READ),
            RoleName.PHARMACIST, List.of(
                    PATIENTS_READ, DOCTORS_READ,
                    "medications:create", MEDICATIONS_READ, "medications:update", "medications:delete",
                    "medical-inventory:create", MEDICAL_INVENTORY_READ,
                    "medical-inventory:update", "medical-inventory:delete",
                    PRESCRIPTIONS_READ, PRESCRIPTION_ITEMS_READ,
                    NOTIFICATIONS_READ, NOTIFICATIONS_UPDATE)
    );

    /** One sample login per role. Seed credentials for dev/demo use, not production
     *  secrets — logged once below so they're easy to find, the same spirit as Spring
     *  Security's own generated dev password log line. Email is mandatory now (see
     *  {@code UserRequest}) — derived as {@code username@gmail.com} rather than
     *  hand-listed per entry, so it can never drift out of sync with the username. */
    private record SeedUser(String username, String password, RoleName role) {
        String email() {
            return username() + "@gmail.com";
        }
    }

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("admin", "Admin@123", RoleName.ADMIN),
            new SeedUser("doctorjohn", "Doctor@123", RoleName.DOCTOR),
            new SeedUser("receptionist1", "Reception@123", RoleName.RECEPTIONIST),
            new SeedUser("analyst1", "Analyst@123", RoleName.ANALYST),
            new SeedUser("pharmacist1", "Pharmacist@123", RoleName.PHARMACIST)
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final UserService userService;

    @Override
    public void run(String... args) {
        Map<String, String> permissionIds = seedPermissions();
        Map<RoleName, String> roleIds = seedRoles();
        seedGrants(roleIds, permissionIds);
        seedPeople(roleIds);

        log.info("RBAC seed check complete: {} permissions, {} roles, {} seed users available "
                        + "(credentials: admin/Admin@123, doctorjohn/Doctor@123, receptionist1/Reception@123, "
                        + "analyst1/Analyst@123, pharmacist1/Pharmacist@123)",
                permissionIds.size(), roleIds.size(), SEED_USERS.size());
    }

    /**
     * @return every (resource,action) pair's permission id, keyed as {@code "resource:action"}.
     * Creates directly via {@link PermissionRepository} rather than
     * {@code PermissionService} — permissions are a fixed, system-managed catalog
     * (every {@code Resource}<code>×</code>{@code PermissionAction} combination, seeded
     * here and nowhere else) with no ad hoc create/update/delete capability exposed
     * anywhere in the API; this bootstrap is the one legitimate writer.
     */
    private Map<String, String> seedPermissions() {
        Map<String, String> permissionIds = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        for (Resource resource : Resource.values()) {
            for (PermissionAction action : PermissionAction.values()) {
                String resourceValue = resource.getDbValue();
                String actionValue = action.getDbValue();
                String key = resourceValue + ":" + actionValue;
                Permission existing = permissionRepository
                        .findByResourceAndAction(resourceValue, actionValue).orElse(null);
                if (existing != null) {
                    permissionIds.put(key, existing.getPermissionId());
                    continue;
                }
                Permission permission = new Permission();
                permission.setResource(resourceValue);
                permission.setAction(actionValue);
                permission.setCreatedAt(now);
                permission.setUpdatedAt(now);
                permissionIds.put(key, permissionRepository.save(permission).getPermissionId());
            }
        }
        return permissionIds;
    }

    private Map<RoleName, String> seedRoles() {
        Map<RoleName, String> roleIds = new LinkedHashMap<>();
        for (RoleName roleName : RoleName.values()) {
            Role existing = roleRepository.findByRoleName(roleName.getDbValue()).orElse(null);
            if (existing != null) {
                roleIds.put(roleName, existing.getRoleId());
                continue;
            }
            RoleRequest request = new RoleRequest();
            request.setRoleName(roleName.getDbValue());
            request.setDescription(roleName.getDbValue() + " (seeded default role)");
            roleIds.put(roleName, roleService.createRole(request).getRoleId());
        }
        return roleIds;
    }

    private void seedGrants(Map<RoleName, String> roleIds, Map<String, String> permissionIds) {
        for (RoleName roleName : RoleName.values()) {
            String roleId = roleIds.get(roleName);
            List<String> grantKeys = roleName == RoleName.ADMIN
                    ? new ArrayList<>(permissionIds.keySet())
                    : ROLE_GRANTS.getOrDefault(roleName, List.of());
            for (String key : grantKeys) {
                String permissionId = permissionIds.get(key);
                try {
                    roleService.grantPermission(roleId, permissionId);
                } catch (ConflictException _) {
                    // Already granted from a previous startup — nothing to do.
                }
            }
        }
    }

    private void seedPeople(Map<RoleName, String> roleIds) {
        for (SeedUser seed : SEED_USERS) {
            String userId = userRepository.findByUsername(seed.username())
                    .map(user -> user.getUserId())
                    .orElseGet(() -> {
                        UserRequest request = new UserRequest();
                        request.setUsername(seed.username());
                        request.setPassword(seed.password());
                        request.setEmail(seed.email());
                        String newUserId = userService.createUser(request).getUserId();
                        // A seeded demo account has no real owner to prove mailbox
                        // ownership to — self-registration's usual email-verification
                        // gate (AuthService.login) would otherwise lock every one of
                        // these out immediately, the same reasoning
                        // UserService.createUserByAdmin already applies.
                        userRepository.findById(newUserId).ifPresent(user -> {
                            user.setEmailVerifiedAt(LocalDateTime.now(ZoneId.systemDefault()));
                            userRepository.save(user);
                        });
                        return newUserId;
                    });
            try {
                userService.assignRole(userId, roleIds.get(seed.role()));
            } catch (ConflictException _) {
                // Already holds this role from a previous startup — nothing to do.
            }
        }
    }
}
