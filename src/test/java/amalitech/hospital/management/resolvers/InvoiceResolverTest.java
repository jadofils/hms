package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.InvoiceService;
import amalitech.hospital.management.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link InvoiceResolver} — see {@code UserResolverTest}'s Javadoc for the
 *  shared reasoning. */
@GraphQlTest(InvoiceResolver.class)
@Import(GraphQlConfig.class)
class InvoiceResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private InvoiceService invoiceService;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private PatientService patientService;

    private InvoiceResponse existingInvoice() {
        InvoiceResponse response = new InvoiceResponse();
        response.setInvoiceId("inv-1");
        response.setAppointmentId("appt-1");
        response.setPatientId("patient-1");
        response.setTotalAmount(new BigDecimal("100.00"));
        response.setPaymentStatus("unpaid");
        return response;
    }

    @Test
    void invoice_returnsRealPatientAndAppointmentObjects() {
        when(invoiceService.getInvoice("inv-1")).thenReturn(existingInvoice());
        AppointmentResponse appointment = new AppointmentResponse();
        appointment.setAppointmentId("appt-1");
        when(appointmentService.getAppointment("appt-1")).thenReturn(appointment);
        PatientResponse patient = new PatientResponse();
        patient.setPatientId("patient-1");
        patient.setFirstName("Alice");
        when(patientService.getPatient("patient-1")).thenReturn(patient);

        graphQlTester.document("{ invoice(invoiceId: \"inv-1\") { totalAmount paymentStatus patient { firstName } } }")
                .execute()
                .path("invoice.totalAmount").entity(BigDecimal.class).isEqualTo(new BigDecimal("100.00"))
                .path("invoice.patient.firstName").entity(String.class).isEqualTo("Alice");

        verify(invoiceService).getInvoice("inv-1");
    }

    @Test
    void createInvoice_delegatesToService() {
        when(invoiceService.createInvoice(any())).thenReturn(existingInvoice());

        graphQlTester.document(
                        "mutation { createInvoice(input: { appointmentId: \"appt-1\", patientId: \"patient-1\" }) { invoiceId } }")
                .execute()
                .path("createInvoice.invoiceId").entity(String.class).isEqualTo("inv-1");

        verify(invoiceService).createInvoice(any());
    }

    @Test
    void deleteInvoice_returnsTrue() {
        graphQlTester.document("mutation { deleteInvoice(invoiceId: \"inv-1\") }")
                .execute()
                .path("deleteInvoice").entity(Boolean.class).isEqualTo(true);

        verify(invoiceService).deleteInvoice("inv-1");
    }
}
