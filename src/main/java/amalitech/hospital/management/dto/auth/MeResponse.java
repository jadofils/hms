package amalitech.hospital.management.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MeResponse {
    private String userId;
    private String username;
    private String email;
    private Boolean isActive;
    /** Every role claim embedded in the caller's own Bearer token at login — a user can
     *  hold several roles simultaneously (see CLAUDE.md's User↔Role many-to-many note),
     *  so this is a list, not a single name. Reflects a role change (assignRoles/
     *  revokeRole) only once the caller obtains a new token, same as any other
     *  JWT-embedded claim. Every other field here is a live, cache-backed lookup (see
     *  AuthController.me), so it reflects a profile update (PUT /api/v1/users/{userId})
     *  immediately, unlike this one. */
    private List<String> roles;
}
