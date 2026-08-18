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
    /** Populated by both the single-item lookup ({@code UserService.getUser}) and the
     *  paginated listing ({@code UserService.getUsers}, via
     *  {@code UserService.attachRolesAndDoctors}) — unlike most nested collections in
     *  this codebase (e.g. {@code DoctorResponse.departments}), which are eager-loaded
     *  only on the single-item lookup. Depth differs between the two: {@code getUser}
     *  eager-loads each role down to its own permissions too ({@code
     *  UserService.toRoleResponse}); the paginated listing deliberately stays shallower
     *  — role id/name/description only, no nested permissions ({@code
     *  UserService.toShallowRoleResponse}) — since expanding every listed user's every
     *  role's full permission set is more than a listing needs. Empty (never {@code
     *  null}) for a user holding no active role. */
    private List<RoleResponse> roles;
    /** {@code null} for a user with no linked doctor record ({@code User.doctor} is a
     *  nullable FK). Populated the same way as {@code roles} above — both by
     *  {@code UserService.getUser} and by the paginated {@code UserService.getUsers} —
     *  reusing {@code DoctorService.getDoctor} so it comes back with its own departments
     *  already eager-loaded too. */
    private DoctorResponse doctor;
}
