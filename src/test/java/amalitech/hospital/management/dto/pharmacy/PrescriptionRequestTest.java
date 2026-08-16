package amalitech.hospital.management.dto.pharmacy;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Especially provokes {@code dateIssued} — a prescription can never be dated in the
 *  future, the same direction as {@code PatientRequest.dob}. */
class PrescriptionRequestTest extends ValidationTestBase {

    private static PrescriptionRequest valid() {
        PrescriptionRequest request = new PrescriptionRequest();
        request.setAppointmentId("appt-1");
        request.setDateIssued(LocalDate.now());
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void dateIssuedIsOptional() {
        PrescriptionRequest request = valid();
        request.setDateIssued(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void dateIssued_today_isAccepted() {
        PrescriptionRequest request = valid();
        request.setDateIssued(LocalDate.now());
        assertThat(hasViolationOn(request, "dateIssued")).isFalse();
    }

    @Test
    void dateIssued_pastDates_areAccepted() {
        PrescriptionRequest request = valid();
        request.setDateIssued(LocalDate.now().minusYears(1));
        assertThat(hasViolationOn(request, "dateIssued")).isFalse();
    }

    @Test
    void dateIssued_tomorrow_isRejected() {
        PrescriptionRequest request = valid();
        request.setDateIssued(LocalDate.now().plusDays(1));
        assertThat(hasViolationOn(request, "dateIssued")).isTrue();
    }

    @Test
    void dateIssued_farFuture_isRejected() {
        PrescriptionRequest request = valid();
        request.setDateIssued(LocalDate.now().plusYears(10));
        assertThat(hasViolationOn(request, "dateIssued")).isTrue();
    }

    @Test
    void blankAppointmentId_isRejected() {
        PrescriptionRequest request = valid();
        request.setAppointmentId("");
        assertThat(hasViolationOn(request, "appointmentId")).isTrue();
    }
}
