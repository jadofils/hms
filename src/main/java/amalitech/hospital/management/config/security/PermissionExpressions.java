package amalitech.hospital.management.config.security;

import amalitech.hospital.management.repository.user.role.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Backs {@code @PreAuthorize}'s permission-based expressions (HMS v4, Epic 4.2) — e.g.
 * {@code @PreAuthorize("@permissionCheck.has('roles', 'create')")} on
 * {@code RoleController.createRole}. Deliberately checks a <em>granted permission</em>
 * (resource:action), never a role name: this project is permission-based access
 * control (ARAC), not role-based (RBAC) — a role here is just a named, admin-editable
 * bundle of permissions (see {@code RoleService.grantPermission}/{@code revokePermission}),
 * and nothing should ever be gated on which bundle a caller happens to hold rather than
 * what it's actually been granted. An earlier version of this class's callers used
 * {@code @PreAuthorize("hasRole('ADMIN')")} — replaced with this once that distinction
 * was made explicit, since a literal role check is exactly what this project doesn't
 * want anywhere, {@code @PreAuthorize} included.
 *
 * <p>Delegates to the exact same {@code RolePermissionRepository.hasGrantedPermission}
 * query {@code AuthorizationAspect} already runs for every {@code @RequirePermission}
 * check elsewhere in the app — same source of truth, same real DB-backed grant, just
 * reachable from a {@code @PreAuthorize} SpEL expression instead of a custom
 * {@code @Aspect}, since HMS v4's Epic 4.2 specifically asks for {@code @PreAuthorize}/
 * {@code @Secured} to be the annotation demonstrated on these endpoints. Both checks
 * running on the same method (this bean via {@code @PreAuthorize}, plus the existing
 * {@code @RequirePermission}) is intentional defense-in-depth — two independently
 * implemented enforcement paths agreeing on the identical permission fact, not two
 * different facts stacked like the role+permission layering this replaced.
 *
 * <p>Not Spring Security's own {@code PermissionEvaluator}/{@code hasPermission(...)}
 * SpEL built-in: as of Spring Security 7, wiring a custom {@code PermissionEvaluator}
 * into that built-in requires subclassing {@code DefaultMethodSecurityExpressionHandler}
 * and downcasting its returned {@code MethodSecurityExpressionOperations} to the
 * concrete {@code SecurityExpressionRoot} just to reach a setter that interface no
 * longer exposes — fragile across Spring Security versions for no real benefit over a
 * plain named bean reference, which does the identical job and is far easier to read,
 * test, and keep working across an upgrade.
 */
@Component("permissionCheck")
@RequiredArgsConstructor
public class PermissionExpressions {

    private final RolePermissionRepository rolePermissionRepository;

    /**
     * {@code resource}/{@code action} are the same DB values {@code Resource}/
     * {@code PermissionAction}'s {@code getDbValue()} produce (e.g. {@code "roles"},
     * {@code "create"}) — passed as literal SpEL string arguments rather than the enum
     * itself, since {@code @PreAuthorize}'s SpEL has no clean way to reference an enum
     * constant without a verbose {@code T(...)} type expression.
     */
    public boolean has(String resource, String action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return rolePermissionRepository.hasGrantedPermission(user.roles(), resource, action);
    }
}
