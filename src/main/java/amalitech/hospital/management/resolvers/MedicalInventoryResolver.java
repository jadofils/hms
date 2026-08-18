package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.pharmacy.MedicalInventoryRequest;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicalInventoryService;
import amalitech.hospital.management.service.MedicationService;
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
 * GraphQL front door for {@link MedicalInventoryService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class MedicalInventoryResolver {

    private final MedicalInventoryService medicalInventoryService;
    private final MedicationService medicationService;

    @QueryMapping
    public List<MedicalInventoryResponse> inventoryRecords(@Argument int page, @Argument int size, @Argument String sort) {
        return medicalInventoryService.getInventoryRecords(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public MedicalInventoryResponse inventoryRecord(@Argument String inventoryId) {
        return medicalInventoryService.getInventoryRecord(inventoryId);
    }

    @MutationMapping
    public MedicalInventoryResponse createInventoryRecord(@Argument @Valid MedicalInventoryRequest input) {
        return medicalInventoryService.createInventoryRecord(input);
    }

    @MutationMapping
    public MedicalInventoryResponse updateInventoryRecord(@Argument String inventoryId, @Argument @Valid MedicalInventoryRequest input) {
        return medicalInventoryService.updateInventoryRecord(inventoryId, input);
    }

    @MutationMapping
    public boolean deleteInventoryRecord(@Argument String inventoryId) {
        medicalInventoryService.deleteInventoryRecord(inventoryId);
        return true;
    }

    @SchemaMapping(typeName = "MedicalInventory", field = "medication")
    public MedicationResponse medication(MedicalInventoryResponse inventory) {
        return medicationService.getMedication(inventory.getMedicationId());
    }
}
