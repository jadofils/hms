package amalitech.hospital.management.dto.auth;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForgotPasswordRequestTest extends ValidationTestBase {

    private static ForgotPasswordRequest valid() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void blankEmail_isRejected() {
        ForgotPasswordRequest request = valid();
        request.setEmail("");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void malformedEmail_isRejected() {
        for (String bad : new String[]{"not-an-email", "missing-at.com", "@no-local-part.com", "spaces in@email.com"}) {
            ForgotPasswordRequest request = valid();
            request.setEmail(bad);
            assertThat(hasViolationOn(request, "email")).as("email '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void emailOver100Characters_isRejected() {
        ForgotPasswordRequest request = valid();
        request.setEmail("a".repeat(95) + "@ex.com"); // > 100 chars total
        assertThat(hasViolationOn(request, "email")).isTrue();
    }
}
