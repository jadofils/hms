package amalitech.hospital.management.dto.lab;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Especially provokes {@code completedAt} — a lab result can't be recorded as completed
 *  at a moment that hasn't happened yet, the same direction as
 *  {@code PrescriptionRequest.dateIssued}. */
class LabResultRequestTest extends ValidationTestBase {

    private static LabResultRequest valid() {
        LabResultRequest request = new LabResultRequest();
        request.setResultValue("5.2");
        request.setUnit("mmol/L");
        request.setReferenceRange("3.9-5.6");
        request.setIsAbnormal(false);
        request.setCompletedAt(LocalDateTime.now().minusHours(1));
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void everyFieldIsOptional() {
        assertThat(validate(new LabResultRequest())).isEmpty();
    }

    // ── completedAt ──────────────────────────────────────────────────────────

    @Test
    void completedAt_now_isAccepted() {
        LabResultRequest request = valid();
        request.setCompletedAt(LocalDateTime.now());
        assertThat(hasViolationOn(request, "completedAt")).isFalse();
    }

    @Test
    void completedAt_past_isAccepted() {
        LabResultRequest request = valid();
        request.setCompletedAt(LocalDateTime.now().minusDays(1));
        assertThat(hasViolationOn(request, "completedAt")).isFalse();
    }

    @Test
    void completedAt_future_isRejected() {
        LabResultRequest request = valid();
        request.setCompletedAt(LocalDateTime.now().plusMinutes(5));
        assertThat(hasViolationOn(request, "completedAt")).isTrue();
    }

    @Test
    void completedAt_farFuture_isRejected() {
        LabResultRequest request = valid();
        request.setCompletedAt(LocalDateTime.now().plusYears(1));
        assertThat(hasViolationOn(request, "completedAt")).isTrue();
    }

    // ── other fields ─────────────────────────────────────────────────────────

    @Test
    void resultValueOver100Characters_isRejected() {
        LabResultRequest request = valid();
        request.setResultValue("a".repeat(101));
        assertThat(hasViolationOn(request, "resultValue")).isTrue();
    }

    @Test
    void unitOver20Characters_isRejected() {
        LabResultRequest request = valid();
        request.setUnit("a".repeat(21));
        assertThat(hasViolationOn(request, "unit")).isTrue();
    }

    @Test
    void referenceRangeOver50Characters_isRejected() {
        LabResultRequest request = valid();
        request.setReferenceRange("a".repeat(51));
        assertThat(hasViolationOn(request, "referenceRange")).isTrue();
    }
}
