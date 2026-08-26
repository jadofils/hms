package amalitech.hospital.management.dto.invite;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InviteResponse {
    private String inviteId;
    private String email;
    private String roleId;
    private String roleName;
    private String invitedByUserId;
    private String invitedByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime revokedAt;
}
