package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.dto.pharmacy.PatchPrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.service.MedicationService;
import amalitech.hospital.management.service.PrescriptionItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link PrescriptionItemService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class PrescriptionItemResolver {

    private final PrescriptionItemService prescriptionItemService;
    private final MedicationService medicationService;

    @QueryMapping
    public List<PrescriptionItemResponse> prescriptionItems(@Argument String prescriptionId) {
        return prescriptionItemService.getItems(prescriptionId);
    }

    @MutationMapping
    public PrescriptionItemResponse createPrescriptionItem(@Argument String prescriptionId, @Argument @Valid PrescriptionItemRequest input) {
        return prescriptionItemService.createItem(prescriptionId, input);
    }

    @MutationMapping
    public PrescriptionItemResponse updatePrescriptionItem(@Argument String prescriptionId, @Argument String itemId,
            @Argument @Valid PrescriptionItemRequest input) {
        return prescriptionItemService.updateItem(prescriptionId, itemId, input);
    }

    @MutationMapping
    public PrescriptionItemResponse patchPrescriptionItem(@Argument String prescriptionId, @Argument String itemId,
            @Argument @Valid PatchPrescriptionItemRequest input) {
        return prescriptionItemService.patchItem(prescriptionId, itemId, input);
    }

    @MutationMapping
    public boolean deletePrescriptionItem(@Argument String prescriptionId, @Argument String itemId) {
        prescriptionItemService.deleteItem(prescriptionId, itemId);
        return true;
    }

    @SchemaMapping(typeName = "PrescriptionItem", field = "medication")
    public MedicationResponse medication(PrescriptionItemResponse item) {
        return medicationService.getMedication(item.getMedicationId());
    }
}
