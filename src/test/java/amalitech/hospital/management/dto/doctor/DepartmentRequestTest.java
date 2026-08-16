package amalitech.hospital.management.dto.doctor;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentRequestTest extends ValidationTestBase {

    private static DepartmentRequest valid() {
        DepartmentRequest request = new DepartmentRequest();
        request.setName("Cardiology");
        request.setLocation("Building A, 2nd Floor");
        request.setPhone("1234567");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void nameAllowsCommonPunctuation() {
        DepartmentRequest request = valid();
        request.setName("Cardiology & Vascular Surgery, Unit-1");
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        DepartmentRequest request = valid();
        request.setName("");
        assertThat(hasViolationOn(request, "name")).isTrue();
    }

    @Test
    void nameWithDisallowedSymbols_isRejected() {
        for (String bad : new String[]{"Cardiology!", "Cardiology#1", "Cardiology*"}) {
            DepartmentRequest request = valid();
            request.setName(bad);
            assertThat(hasViolationOn(request, "name")).as("name '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void nameOver100Characters_isRejected() {
        DepartmentRequest request = valid();
        request.setName("a".repeat(101));
        assertThat(hasViolationOn(request, "name")).isTrue();
    }

    @Test
    void locationAndPhone_areOptional() {
        DepartmentRequest request = valid();
        request.setLocation(null);
        request.setPhone(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void malformedPhone_isRejected() {
        DepartmentRequest request = valid();
        request.setPhone("not-a-phone");
        assertThat(hasViolationOn(request, "phone")).isTrue();
    }
}
