package amalitech.hospital.management.dto.auth;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTest extends ValidationTestBase {

    private static LoginRequest valid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("whatever");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void blankUsername_isRejected() {
        LoginRequest request = valid();
        request.setUsername("");
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void nullUsername_isRejected() {
        LoginRequest request = valid();
        request.setUsername(null);
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void blankPassword_isRejected() {
        LoginRequest request = valid();
        request.setPassword("   ");
        assertThat(hasViolationOn(request, "password")).isTrue();
    }
}
