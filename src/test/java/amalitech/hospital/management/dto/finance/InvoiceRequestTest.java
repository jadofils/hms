package amalitech.hospital.management.dto.finance;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceRequestTest extends ValidationTestBase {

    private static InvoiceRequest valid() {
        InvoiceRequest request = new InvoiceRequest();
        request.setAppointmentId("appt-1");
        request.setPatientId("patient-1");
        request.setTotalAmount(new BigDecimal("150.00"));
        request.setPaymentStatus("unpaid");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void totalAmountAndPaymentStatus_areOptional() {
        InvoiceRequest request = valid();
        request.setTotalAmount(null);
        request.setPaymentStatus(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankAppointmentOrPatientId_isRejected() {
        InvoiceRequest request = valid();
        request.setAppointmentId("");
        assertThat(hasViolationOn(request, "appointmentId")).isTrue();

        request = valid();
        request.setPatientId(" ");
        assertThat(hasViolationOn(request, "patientId")).isTrue();
    }

    @Test
    void negativeTotalAmount_isRejected() {
        InvoiceRequest request = valid();
        request.setTotalAmount(new BigDecimal("-0.01"));
        assertThat(hasViolationOn(request, "totalAmount")).isTrue();
    }

    @Test
    void totalAmountWithTooManyIntegerDigits_isRejected() {
        InvoiceRequest request = valid();
        request.setTotalAmount(new BigDecimal("123456789.00"));
        assertThat(hasViolationOn(request, "totalAmount")).isTrue();
    }

    @Test
    void totalAmountWithTooManyDecimalPlaces_isRejected() {
        InvoiceRequest request = valid();
        request.setTotalAmount(new BigDecimal("100.999"));
        assertThat(hasViolationOn(request, "totalAmount")).isTrue();
    }

    @Test
    void invalidPaymentStatus_isRejected() {
        InvoiceRequest request = valid();
        request.setPaymentStatus("refunded");
        assertThat(hasViolationOn(request, "paymentStatus")).isTrue();
    }

    @Test
    void paymentStatus_everyAllowedValue_isCaseInsensitivelyAccepted() {
        for (String ok : new String[]{"unpaid", "PARTIALLY_PAID", "Paid"}) {
            InvoiceRequest request = valid();
            request.setPaymentStatus(ok);
            assertThat(hasViolationOn(request, "paymentStatus")).as("status '%s' should be accepted", ok).isFalse();
        }
    }
}
