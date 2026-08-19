package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.PrescriptionItemService;
import amalitech.hospital.management.service.PrescriptionService;
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
 * GraphQL front door for {@link PrescriptionService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs). Line items
 * are managed separately (see {@code PrescriptionItemResolver}), same split as REST's
 * {@code PrescriptionController}/{@code PrescriptionItemController}.
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class PrescriptionResolver {

    private final PrescriptionService prescriptionService;
    private final AppointmentService appointmentService;
    private final PrescriptionItemService prescriptionItemService;

    @QueryMapping
    public List<PrescriptionResponse> prescriptions(@Argument int page, @Argument int size, @Argument String sort,
            @Argument String patientId) {
        return prescriptionService.getPrescriptions(GraphQlPaging.of(page, size, sort), patientId).getContent();
    }

    @QueryMapping
    public PrescriptionResponse prescription(@Argument String prescriptionId) {
        return prescriptionService.getPrescription(prescriptionId);
    }

    @MutationMapping
    public PrescriptionResponse createPrescription(@Argument @Valid PrescriptionRequest input) {
        return prescriptionService.createPrescription(input);
    }

    @MutationMapping
    public PrescriptionResponse updatePrescription(@Argument String prescriptionId, @Argument @Valid PrescriptionRequest input) {
        return prescriptionService.updatePrescription(prescriptionId, input);
    }

    @MutationMapping
    public boolean deletePrescription(@Argument String prescriptionId) {
        prescriptionService.deletePrescription(prescriptionId);
        return true;
    }

    @SchemaMapping(typeName = "Prescription", field = "appointment")
    public AppointmentResponse appointment(PrescriptionResponse prescription) {
        return appointmentService.getAppointment(prescription.getAppointmentId());
    }

    @SchemaMapping(typeName = "Prescription", field = "items")
    public List<PrescriptionItemResponse> items(PrescriptionResponse prescription) {
        return prescriptionItemService.getItems(prescription.getPrescriptionId());
    }
}
