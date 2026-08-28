package amalitech.hospital.management.config.security;

import java.util.List;

/**
 * Principal stored in the {@code SecurityContext} once {@link JwtAuthenticationFilter}
 * verifies a Bearer token — carries the same identity fields that are encrypted inside
 * the JWT itself (see {@link JwtService}). {@code roles} is every active role the caller
 * held at login time (a user can hold several simultaneously) — every permission check
 * against this principal (see {@code AuthorizationAspect}/{@code PermissionExpressions})
 * treats a permission as granted if <em>any</em> of these roles grants it.
 */
public record AuthenticatedUser(String userId, String username, List<String> roles) {}
