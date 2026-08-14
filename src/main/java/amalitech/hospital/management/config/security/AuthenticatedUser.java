package amalitech.hospital.management.config.security;

/**
 * Principal stored in the {@code SecurityContext} once {@link JwtAuthenticationFilter}
 * verifies a Bearer token — carries the same three identity fields that are encrypted
 * inside the JWT itself (see {@link JwtService}).
 */
public record AuthenticatedUser(String userId, String username, String role) {}
