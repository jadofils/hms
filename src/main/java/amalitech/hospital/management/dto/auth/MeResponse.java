package amalitech.hospital.management.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MeResponse {
    private String userId;
    private String username;
    private String role;
}
