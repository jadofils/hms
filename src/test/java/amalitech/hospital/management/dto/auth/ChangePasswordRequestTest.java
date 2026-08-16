package amalitech.hospital.management.dto.auth;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePasswordRequestTest extends ValidationTestBase {

    private static ChangePasswordRequest valid() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("whatever");
        request.setNewPassword("NewPass1!");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void blankCurrentPassword_isRejected() {
        ChangePasswordRequest request = valid();
        request.setCurrentPassword("");
        assertThat(hasViolationOn(request, "currentPassword")).isTrue();
    }

    @Test
    void weakNewPassword_isRejected_missingEachRequiredCharacterClass() {
        for (String weak : new String[]{
                "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecialChar1", "Short1!"
        }) {
            ChangePasswordRequest request = valid();
            request.setNewPassword(weak);
            assertThat(hasViolationOn(request, "newPassword")).as("password '%s' should be rejected", weak).isTrue();
        }
    }

    @Test
    void newPasswordOver64Characters_isRejected() {
        ChangePasswordRequest request = valid();
        request.setNewPassword("Aa1!" + "a".repeat(62));
        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }
}
