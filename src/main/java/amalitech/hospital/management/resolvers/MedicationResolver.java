package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link MedicationService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class MedicationResolver {

    private final MedicationService medicationService;

    @QueryMapping
    public List<MedicationResponse> medications(@Argument int page, @Argument int size, @Argument String sort) {
        return medicationService.getMedications(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public MedicationResponse medication(@Argument String medicationId) {
        return medicationService.getMedication(medicationId);
    }

    @MutationMapping
    public MedicationResponse createMedication(@Argument @Valid MedicationRequest input) {
        return medicationService.createMedication(input);
    }

    @MutationMapping
    public MedicationResponse updateMedication(@Argument String medicationId, @Argument @Valid MedicationRequest input) {
        return medicationService.updateMedication(medicationId, input);
    }

    @MutationMapping
    public boolean deleteMedication(@Argument String medicationId) {
        medicationService.deleteMedication(medicationId);
        return true;
    }
}
