package amalitech.hospital.management.dto.doctor;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorRequestTest extends ValidationTestBase {

    private static DoctorRequest valid() {
        DoctorRequest request = new DoctorRequest();
        request.setFirstName("Greg");
        request.setLastName("House");
        request.setSpecialization("Diagnostics");
        request.setPhone("1234567");
        request.setEmail("house@example.com");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void specializationPhoneAndEmail_areOptional() {
        DoctorRequest request = valid();
        request.setSpecialization(null);
        request.setPhone(null);
        request.setEmail(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankFirstOrLastName_isRejected() {
        DoctorRequest request = valid();
        request.setFirstName("");
        assertThat(hasViolationOn(request, "firstName")).isTrue();

        request = valid();
        request.setLastName(" ");
        assertThat(hasViolationOn(request, "lastName")).isTrue();
    }

    @Test
    void nameWithDigits_isRejected() {
        DoctorRequest request = valid();
        request.setFirstName("Greg2");
        assertThat(hasViolationOn(request, "firstName")).isTrue();
    }

    @Test
    void specializationWithDigits_isRejected() {
        DoctorRequest request = valid();
        request.setSpecialization("Cardiology2");
        assertThat(hasViolationOn(request, "specialization")).isTrue();
    }

    @Test
    void malformedPhone_isRejected() {
        DoctorRequest request = valid();
        request.setPhone("phone-number");
        assertThat(hasViolationOn(request, "phone")).isTrue();
    }

    @Test
    void malformedEmail_isRejected() {
        DoctorRequest request = valid();
        request.setEmail("not-an-email");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }
}
