package amalitech.hospital.management.dto.user.role.permission;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionRequestTest extends ValidationTestBase {

    private static PermissionRequest valid() {
        PermissionRequest request = new PermissionRequest();
        request.setResource("patients");
        request.setAction("read");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void hyphenatedResourceWithDigits_isAccepted() {
        PermissionRequest request = valid();
        request.setResource("test-resource-123456789");
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankResource_isRejected() {
        PermissionRequest request = valid();
        request.setResource("");
        assertThat(hasViolationOn(request, "resource")).isTrue();
    }

    @Test
    void uppercaseOrInvalidResource_isRejected() {
        for (String bad : new String[]{"Patients", "PATIENTS", "patients_all", "patients ", "-patients", "1patients"}) {
            PermissionRequest request = valid();
            request.setResource(bad);
            assertThat(hasViolationOn(request, "resource")).as("resource '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void blankAction_isRejected() {
        PermissionRequest request = valid();
        request.setAction(" ");
        assertThat(hasViolationOn(request, "action")).isTrue();
    }

    @Test
    void uppercaseOrInvalidAction_isRejected() {
        for (String bad : new String[]{"Read", "READ", "read_all", "1read"}) {
            PermissionRequest request = valid();
            request.setAction(bad);
            assertThat(hasViolationOn(request, "action")).as("action '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void resourceOver50Characters_isRejected() {
        PermissionRequest request = valid();
        request.setResource("a".repeat(51));
        assertThat(hasViolationOn(request, "resource")).isTrue();
    }
}
