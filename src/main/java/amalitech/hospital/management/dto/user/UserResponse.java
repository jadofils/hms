package amalitech.hospital.management.dto.user;

import lombok.Data;

@Data
public class UserResponse {
    private String userId;
    private String username;
    private String email;
    private Boolean isActive;
}