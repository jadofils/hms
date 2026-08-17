package amalitech.hospital.management.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MeResponse {
    private String userId;
    private String username;
    private String email;
    private Boolean isActive;
    /** The role claim embedded in the caller's own Bearer token at login — reflects a
     *  role change (assignRole/revokeRole) only once the caller obtains a new token,
     *  same as any other JWT-embedded claim. Every other field here is a live,
     *  cache-backed lookup (see AuthController.me), so it reflects a profile update
     *  (PUT /api/v1/users/{userId}) immediately, unlike this one. */
    private String role;
}
