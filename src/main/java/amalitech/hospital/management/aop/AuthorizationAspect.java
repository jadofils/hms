package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.config.security.AuthenticatedUser;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.repository.user.role.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Enforces {@code @RequirePermission} on controller methods — runs {@code @Before} the
 * annotated method, so a caller lacking the declared {@code resource:action} never
 * reaches the controller body (and therefore never reaches the service/DB layer) at all.
 *
 * Reads the {@link AuthenticatedUser} principal {@code JwtAuthenticationFilter} already
 * puts on {@link SecurityContextHolder} for every request carrying a valid Bearer
 * token — no new authentication plumbing needed, this only adds the authorization check
 * {@code SecurityConfig}'s deliberate {@code anyRequest().permitAll()} doesn't perform
 * itself (see that class's Javadoc).
 *
 * Checks by <b>permission</b>, not by a hardcoded role list: a role added later only
 * needs to be granted the relevant permission (via the existing
 * {@code POST /api/v1/roles/{roleId}/permissions/{permissionId}}) to satisfy any
 * {@code @RequirePermission} anywhere — no code change, no redeploy.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final RolePermissionRepository rolePermissionRepository;

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No token provided");
        }

        String resource = requirePermission.resource().getDbValue();
        String action = requirePermission.action().getDbValue();
        boolean granted = rolePermissionRepository.hasGrantedPermission(user.role(), resource, action);
        if (!granted) {
            throw new AccessDeniedException(
                    "Role '" + user.role() + "' lacks permission " + resource + ":" + action);
        }
    }
}
