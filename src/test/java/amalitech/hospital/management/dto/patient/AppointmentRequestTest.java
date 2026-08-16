package amalitech.hospital.management.dto.patient;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Especially provokes {@code appointmentDate} — an appointment must always be booked
 *  for a moment strictly in the future, the mirror-image constraint of a birth date. */
class AppointmentRequestTest extends ValidationTestBase {

    private static AppointmentRequest valid() {
        AppointmentRequest request = new AppointmentRequest();
        request.setPatientId("patient-1");
        request.setDoctorId("doctor-1");
        request.setAppointmentDate(LocalDateTime.now().plusDays(1));
        request.setReason("Checkup");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    // ── appointmentDate ──────────────────────────────────────────────────────

    @Test
    void appointmentDate_null_isRejected() {
        AppointmentRequest request = valid();
        request.setAppointmentDate(null);
        assertThat(hasViolationOn(request, "appointmentDate")).isTrue();
    }

    @Test
    void appointmentDate_thisExactMoment_isRejected() {
        // @Future requires strictly after "now" — a booking for "right now" isn't
        // actually schedulable and must not slip through.
        AppointmentRequest request = valid();
        request.setAppointmentDate(LocalDateTime.now());
        assertThat(hasViolationOn(request, "appointmentDate")).isTrue();
    }

    @Test
    void appointmentDate_yesterday_isRejected() {
        AppointmentRequest request = valid();
        request.setAppointmentDate(LocalDateTime.now().minusDays(1));
        assertThat(hasViolationOn(request, "appointmentDate")).isTrue();
    }

    @Test
    void appointmentDate_farPast_isRejected() {
        AppointmentRequest request = valid();
        request.setAppointmentDate(LocalDateTime.now().minusYears(1));
        assertThat(hasViolationOn(request, "appointmentDate")).isTrue();
    }

    @Test
    void appointmentDate_oneSecondFromNow_isAccepted() {
        AppointmentRequest request = valid();
        request.setAppointmentDate(LocalDateTime.now().plusSeconds(1));
        assertThat(hasViolationOn(request, "appointmentDate")).isFalse();
    }

    @Test
    void appointmentDate_farFuture_isAccepted() {
        AppointmentRequest request = valid();
        request.setAppointmentDate(LocalDateTime.now().plusYears(5));
        assertThat(hasViolationOn(request, "appointmentDate")).isFalse();
    }

    // ── other fields ─────────────────────────────────────────────────────────

    @Test
    void blankPatientOrDoctorId_isRejected() {
        AppointmentRequest request = valid();
        request.setPatientId("");
        assertThat(hasViolationOn(request, "patientId")).isTrue();

        request = valid();
        request.setDoctorId(" ");
        assertThat(hasViolationOn(request, "doctorId")).isTrue();
    }

    @Test
    void reasonOver255Characters_isRejected() {
        AppointmentRequest request = valid();
        request.setReason("a".repeat(256));
        assertThat(hasViolationOn(request, "reason")).isTrue();
    }

    @Test
    void reasonIsOptional() {
        AppointmentRequest request = valid();
        request.setReason(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void invalidStatus_isRejected() {
        AppointmentRequest request = valid();
        request.setStatus("in-progress");
        assertThat(hasViolationOn(request, "status")).isTrue();
    }

    @Test
    void statusIsCaseInsensitive() {
        for (String ok : new String[]{"scheduled", "SCHEDULED", "Completed", "cancelled"}) {
            AppointmentRequest request = valid();
            request.setStatus(ok);
            assertThat(hasViolationOn(request, "status")).as("status '%s' should be accepted", ok).isFalse();
        }
    }
}
