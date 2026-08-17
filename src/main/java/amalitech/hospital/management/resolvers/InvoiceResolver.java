package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.finance.InvoiceRequest;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.InvoiceService;
import amalitech.hospital.management.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link InvoiceService} — see {@code UserResolver}'s Javadoc for
 * the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class InvoiceResolver {

    private final InvoiceService invoiceService;
    private final AppointmentService appointmentService;
    private final PatientService patientService;

    @QueryMapping
    public List<InvoiceResponse> invoices(@Argument int page, @Argument int size) {
        return invoiceService.getInvoices(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public InvoiceResponse invoice(@Argument String invoiceId) {
        return invoiceService.getInvoice(invoiceId);
    }

    @MutationMapping
    public InvoiceResponse createInvoice(@Argument @Valid InvoiceRequest input) {
        return invoiceService.createInvoice(input);
    }

    @MutationMapping
    public InvoiceResponse updateInvoice(@Argument String invoiceId, @Argument @Valid InvoiceRequest input) {
        return invoiceService.updateInvoice(invoiceId, input);
    }

    @MutationMapping
    public boolean deleteInvoice(@Argument String invoiceId) {
        invoiceService.deleteInvoice(invoiceId);
        return true;
    }

    @SchemaMapping(typeName = "Invoice", field = "appointment")
    public AppointmentResponse appointment(InvoiceResponse invoice) {
        return appointmentService.getAppointment(invoice.getAppointmentId());
    }

    @SchemaMapping(typeName = "Invoice", field = "patient")
    public PatientResponse patient(InvoiceResponse invoice) {
        return patientService.getPatient(invoice.getPatientId());
    }
}
