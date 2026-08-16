package amalitech.hospital.management.dto.user.role;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRequestTest extends ValidationTestBase {

    private static RoleRequest valid() {
        RoleRequest request = new RoleRequest();
        request.setRoleName("Head Nurse");
        request.setDescription("Manages the nursing staff");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void descriptionIsOptional() {
        RoleRequest request = valid();
        request.setDescription(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void roleNameWithDigits_isAccepted() {
        // Real-world names like "Tier2Support", and test fixtures that suffix a role name
        // with digits for uniqueness, both need to keep working.
        RoleRequest request = valid();
        request.setRoleName("TestRole123456");
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankRoleName_isRejected() {
        RoleRequest request = valid();
        request.setRoleName(" ");
        assertThat(hasViolationOn(request, "roleName")).isTrue();
    }

    @Test
    void roleNameStartingWithDigit_isRejected() {
        RoleRequest request = valid();
        request.setRoleName("1Admin");
        assertThat(hasViolationOn(request, "roleName")).isTrue();
    }

    @Test
    void roleNameWithInvalidCharacters_isRejected() {
        for (String bad : new String[]{"Admin!", "Admin_2", "Admin@Home", "Admin/Ops"}) {
            RoleRequest request = valid();
            request.setRoleName(bad);
            assertThat(hasViolationOn(request, "roleName")).as("role name '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void roleNameOver50Characters_isRejected() {
        RoleRequest request = valid();
        request.setRoleName("A" + "a".repeat(50));
        assertThat(hasViolationOn(request, "roleName")).isTrue();
    }

    @Test
    void descriptionOver255Characters_isRejected() {
        RoleRequest request = valid();
        request.setDescription("a".repeat(256));
        assertThat(hasViolationOn(request, "description")).isTrue();
    }
}
