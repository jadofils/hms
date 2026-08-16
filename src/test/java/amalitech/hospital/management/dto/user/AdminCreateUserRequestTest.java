package amalitech.hospital.management.dto.user;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCreateUserRequestTest extends ValidationTestBase {

    private static AdminCreateUserRequest valid() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("jado123");
        request.setEmail("jado@example.com");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void blankUsername_isRejected() {
        AdminCreateUserRequest request = valid();
        request.setUsername("");
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void usernameWithNonAlphanumericChars_isRejected() {
        for (String bad : new String[]{"jado-fils", "jado_fils", "jado fils"}) {
            AdminCreateUserRequest request = valid();
            request.setUsername(bad);
            assertThat(hasViolationOn(request, "username")).as("username '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void usernameUnder3OrOver50Characters_isRejected() {
        AdminCreateUserRequest request = valid();
        request.setUsername("ab");
        assertThat(hasViolationOn(request, "username")).isTrue();

        request = valid();
        request.setUsername("a".repeat(51));
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void blankEmail_isRejected() {
        AdminCreateUserRequest request = valid();
        request.setEmail("");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void nullEmail_isRejected() {
        // Unlike UserRequest, email is required here — there'd be no way to deliver the
        // generated password otherwise.
        AdminCreateUserRequest request = valid();
        request.setEmail(null);
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void malformedEmail_isRejected() {
        AdminCreateUserRequest request = valid();
        request.setEmail("not-an-email");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void emailOver100Characters_isRejected() {
        AdminCreateUserRequest request = valid();
        request.setEmail("a".repeat(95) + "@ex.com");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }
}
