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
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link InvoiceService} — see {@code UserResolver}'s Javadoc for
 * the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class InvoiceResolver {

    private final InvoiceService invoiceService;
    private final AppointmentService appointmentService;
    private final PatientService patientService;

    @QueryMapping
    public List<InvoiceResponse> invoices(@Argument int page, @Argument int size, @Argument String sort,
            @Argument String paymentStatus) {
        return invoiceService.getInvoices(GraphQlPaging.of(page, size, sort), paymentStatus).getContent();
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
