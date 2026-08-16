package amalitech.hospital.management.dto.pharmacy;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationRequestTest extends ValidationTestBase {

    private static MedicationRequest valid() {
        MedicationRequest request = new MedicationRequest();
        request.setName("Amoxicillin 500mg");
        request.setGenericName("Amoxicillin Trihydrate");
        request.setForm("capsule");
        request.setUnitPrice(new BigDecimal("2.50"));
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void genericNameFormAndUnitPrice_areOptional() {
        MedicationRequest request = valid();
        request.setGenericName(null);
        request.setForm(null);
        request.setUnitPrice(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        MedicationRequest request = valid();
        request.setName("");
        assertThat(hasViolationOn(request, "name")).isTrue();
    }

    @Test
    void nameWithDisallowedSymbols_isRejected() {
        for (String bad : new String[]{"Amoxicillin#1", "Amoxicillin@Home", "Amoxicillin*"}) {
            MedicationRequest request = valid();
            request.setName(bad);
            assertThat(hasViolationOn(request, "name")).as("name '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void nameOver150Characters_isRejected() {
        MedicationRequest request = valid();
        request.setName("a".repeat(151));
        assertThat(hasViolationOn(request, "name")).isTrue();
    }

    @Test
    void negativeUnitPrice_isRejected() {
        MedicationRequest request = valid();
        request.setUnitPrice(new BigDecimal("-0.01"));
        assertThat(hasViolationOn(request, "unitPrice")).isTrue();
    }

    @Test
    void unitPriceWithTooManyIntegerDigits_isRejected() {
        MedicationRequest request = valid();
        request.setUnitPrice(new BigDecimal("123456789.00")); // 9 integer digits, max is 8
        assertThat(hasViolationOn(request, "unitPrice")).isTrue();
    }

    @Test
    void unitPriceWithTooManyDecimalPlaces_isRejected() {
        MedicationRequest request = valid();
        request.setUnitPrice(new BigDecimal("9.999")); // 3 fraction digits, max is 2
        assertThat(hasViolationOn(request, "unitPrice")).isTrue();
    }

    @Test
    void unitPriceAtPrecisionBoundary_isAccepted() {
        MedicationRequest request = valid();
        request.setUnitPrice(new BigDecimal("99999999.99")); // 8 integer + 2 fraction digits
        assertThat(hasViolationOn(request, "unitPrice")).isFalse();
    }
}
