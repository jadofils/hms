package amalitech.hospital.management.dto.user;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestTest extends ValidationTestBase {

    private static UserRequest valid() {
        UserRequest request = new UserRequest();
        request.setUsername("jado123");
        request.setEmail("jado@example.com");
        request.setPassword("Passw0rd!");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void nullEmail_isRejected() {
        UserRequest request = valid();
        request.setEmail(null);
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void blankEmail_isRejected() {
        UserRequest request = valid();
        request.setEmail("   ");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void blankUsername_isRejected() {
        UserRequest request = valid();
        request.setUsername("");
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void usernameUnder3Characters_isRejected() {
        UserRequest request = valid();
        request.setUsername("ab");
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void usernameOver50Characters_isRejected() {
        UserRequest request = valid();
        request.setUsername("a".repeat(51));
        assertThat(hasViolationOn(request, "username")).isTrue();
    }

    @Test
    void usernameWithNonAlphanumericChars_isRejected() {
        for (String bad : new String[]{"jado-fils", "jado_fils", "jado fils", "jado.fils", "jado@fils"}) {
            UserRequest request = valid();
            request.setUsername(bad);
            assertThat(hasViolationOn(request, "username")).as("username '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void malformedEmail_isRejected() {
        UserRequest request = valid();
        request.setEmail("not-an-email");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void weakPassword_isRejected_missingEachRequiredCharacterClass() {
        for (String weak : new String[]{
                "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecialChar1", "Short1!"
        }) {
            UserRequest request = valid();
            request.setPassword(weak);
            assertThat(hasViolationOn(request, "password")).as("password '%s' should be rejected", weak).isTrue();
        }
    }

    @Test
    void blankPassword_isRejected() {
        UserRequest request = valid();
        request.setPassword("");
        assertThat(hasViolationOn(request, "password")).isTrue();
    }
}
