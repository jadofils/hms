package amalitech.hospital.management.dto.auth;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordRequestTest extends ValidationTestBase {

    private static ResetPasswordRequest valid() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("some-reset-token");
        request.setNewPassword("NewPass1!");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void blankToken_isRejected() {
        ResetPasswordRequest request = valid();
        request.setToken(" ");
        assertThat(hasViolationOn(request, "token")).isTrue();
    }

    @Test
    void weakNewPassword_isRejected_missingEachRequiredCharacterClass() {
        for (String weak : new String[]{
                "alllowercase1!",  // no uppercase
                "ALLUPPERCASE1!",  // no lowercase
                "NoDigitsHere!",   // no digit
                "NoSpecialChar1",  // no special char
                "Short1!"          // under 8 chars
        }) {
            ResetPasswordRequest request = valid();
            request.setNewPassword(weak);
            assertThat(hasViolationOn(request, "newPassword")).as("password '%s' should be rejected", weak).isTrue();
        }
    }

    @Test
    void newPasswordOver64Characters_isRejected() {
        ResetPasswordRequest request = valid();
        request.setNewPassword("Aa1!" + "a".repeat(62)); // 66 chars total
        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }

    @Test
    void blankNewPassword_isRejected() {
        ResetPasswordRequest request = valid();
        request.setNewPassword("");
        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }
}
