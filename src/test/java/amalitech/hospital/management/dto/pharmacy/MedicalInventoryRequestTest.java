package amalitech.hospital.management.dto.pharmacy;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Especially provokes {@code expiryDate} — stock can't be recorded as already expired;
 *  the mirror-image direction of {@code PatientRequest.dob} and
 *  {@code AppointmentRequest.appointmentDate}. */
class MedicalInventoryRequestTest extends ValidationTestBase {

    private static MedicalInventoryRequest valid() {
        MedicalInventoryRequest request = new MedicalInventoryRequest();
        request.setMedicationId("med-1");
        request.setBatchNumber("B123");
        request.setExpiryDate(LocalDate.now().plusYears(1));
        request.setQuantityInStock(50);
        request.setReorderLevel(5);
        request.setSupplier("Acme Pharma");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    // ── expiryDate ───────────────────────────────────────────────────────────

    @Test
    void expiryDate_null_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setExpiryDate(null);
        assertThat(hasViolationOn(request, "expiryDate")).isTrue();
    }

    @Test
    void expiryDate_yesterday_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setExpiryDate(LocalDate.now().minusDays(1));
        assertThat(hasViolationOn(request, "expiryDate")).isTrue();
    }

    @Test
    void expiryDate_farPast_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setExpiryDate(LocalDate.now().minusYears(2));
        assertThat(hasViolationOn(request, "expiryDate")).isTrue();
    }

    @Test
    void expiryDate_today_isAccepted() {
        // Unlike @Past/@Future, @FutureOrPresent's boundary IS inclusive of today — stock
        // expiring today is still valid to record (it hasn't expired *yet*).
        MedicalInventoryRequest request = valid();
        request.setExpiryDate(LocalDate.now());
        assertThat(hasViolationOn(request, "expiryDate")).isFalse();
    }

    @Test
    void expiryDate_farFuture_isAccepted() {
        MedicalInventoryRequest request = valid();
        request.setExpiryDate(LocalDate.now().plusYears(10));
        assertThat(hasViolationOn(request, "expiryDate")).isFalse();
    }

    // ── other fields ─────────────────────────────────────────────────────────

    @Test
    void blankMedicationId_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setMedicationId(" ");
        assertThat(hasViolationOn(request, "medicationId")).isTrue();
    }

    @Test
    void negativeQuantityOrReorderLevel_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setQuantityInStock(-1);
        assertThat(hasViolationOn(request, "quantityInStock")).isTrue();

        request = valid();
        request.setReorderLevel(-1);
        assertThat(hasViolationOn(request, "reorderLevel")).isTrue();
    }

    @Test
    void quantityAndReorderLevel_areOptional() {
        MedicalInventoryRequest request = valid();
        request.setQuantityInStock(null);
        request.setReorderLevel(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void batchNumberOver50Characters_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setBatchNumber("a".repeat(51));
        assertThat(hasViolationOn(request, "batchNumber")).isTrue();
    }

    @Test
    void supplierOver100Characters_isRejected() {
        MedicalInventoryRequest request = valid();
        request.setSupplier("a".repeat(101));
        assertThat(hasViolationOn(request, "supplier")).isTrue();
    }
}
