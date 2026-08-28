package amalitech.hospital.management.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String userId;
    private String username;
    /** Every active role this account held at login time — a user can hold several
     *  simultaneously (see CLAUDE.md's User↔Role many-to-many note), and all of them are
     *  embedded in the returned {@code token} too (see {@code JwtService.generateToken}),
     *  not just one. */
    private List<String> roles;
}
