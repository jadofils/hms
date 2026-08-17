package amalitech.hospital.management.dto.user;

import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import lombok.Data;

import java.util.List;

@Data
public class UserResponse {
    private String userId;
    private String username;
    private String email;
    private Boolean isActive;
    /** Not populated by the paginated listing or by create/update — only by the
     *  single-item lookup ({@code UserService.getUser}), same convention as
     *  {@code DoctorResponse.departments}. Each role here is eager-loaded down to its
     *  own permissions too (see {@code UserService.toRoleResponse}), not just id/name. */
    private List<RoleResponse> roles;
    /** {@code null} for a user with no linked doctor record ({@code User.doctor} is a
     *  nullable FK) — populated the same way, only by {@code UserService.getUser},
     *  reusing {@code DoctorService.getDoctor} so it comes back with its own departments
     *  already eager-loaded too. */
    private DoctorResponse doctor;
}
