package amalitech.hospital.management.dto.lab;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabOrderRequestTest extends ValidationTestBase {

    private static LabOrderRequest valid() {
        LabOrderRequest request = new LabOrderRequest();
        request.setAppointmentId("appt-1");
        request.setDoctorId("doctor-1");
        request.setTestName("Blood Panel");
        request.setStatus("ordered");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void statusIsOptional() {
        LabOrderRequest request = valid();
        request.setStatus(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankAppointmentDoctorOrTestName_isRejected() {
        LabOrderRequest request = valid();
        request.setAppointmentId("");
        assertThat(hasViolationOn(request, "appointmentId")).isTrue();

        request = valid();
        request.setDoctorId(" ");
        assertThat(hasViolationOn(request, "doctorId")).isTrue();

        request = valid();
        request.setTestName("");
        assertThat(hasViolationOn(request, "testName")).isTrue();
    }

    @Test
    void testNameOver150Characters_isRejected() {
        LabOrderRequest request = valid();
        request.setTestName("a".repeat(151));
        assertThat(hasViolationOn(request, "testName")).isTrue();
    }

    @Test
    void invalidStatus_isRejected() {
        LabOrderRequest request = valid();
        request.setStatus("pending");
        assertThat(hasViolationOn(request, "status")).isTrue();
    }

    @Test
    void status_everyAllowedValue_isCaseInsensitivelyAccepted() {
        for (String ok : new String[]{"ordered", "IN_PROGRESS", "Completed", "cancelled"}) {
            LabOrderRequest request = valid();
            request.setStatus(ok);
            assertThat(hasViolationOn(request, "status")).as("status '%s' should be accepted", ok).isFalse();
        }
    }
}
