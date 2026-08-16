package amalitech.hospital.management.dto.pharmacy;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrescriptionItemRequestTest extends ValidationTestBase {

    private static PrescriptionItemRequest valid() {
        PrescriptionItemRequest request = new PrescriptionItemRequest();
        request.setMedicationId("med-1");
        request.setDosage("500mg twice daily");
        request.setQuantity(10);
        request.setInstructions("Take with food");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void dosageAndInstructions_areOptional() {
        PrescriptionItemRequest request = valid();
        request.setDosage(null);
        request.setInstructions(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankMedicationId_isRejected() {
        PrescriptionItemRequest request = valid();
        request.setMedicationId(" ");
        assertThat(hasViolationOn(request, "medicationId")).isTrue();
    }

    @Test
    void quantity_nullOrZeroOrNegative_isRejected() {
        PrescriptionItemRequest request = valid();
        request.setQuantity(null);
        assertThat(hasViolationOn(request, "quantity")).isTrue();

        request = valid();
        request.setQuantity(0);
        assertThat(hasViolationOn(request, "quantity")).isTrue();

        request = valid();
        request.setQuantity(-5);
        assertThat(hasViolationOn(request, "quantity")).isTrue();
    }

    @Test
    void quantityOfOne_isAccepted() {
        PrescriptionItemRequest request = valid();
        request.setQuantity(1);
        assertThat(hasViolationOn(request, "quantity")).isFalse();
    }

    @Test
    void dosageOver50Characters_isRejected() {
        PrescriptionItemRequest request = valid();
        request.setDosage("a".repeat(51));
        assertThat(hasViolationOn(request, "dosage")).isTrue();
    }

    @Test
    void instructionsOver255Characters_isRejected() {
        PrescriptionItemRequest request = valid();
        request.setInstructions("a".repeat(256));
        assertThat(hasViolationOn(request, "instructions")).isTrue();
    }
}
