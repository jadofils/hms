package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link MedicationService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MedicationResolver {

    private final MedicationService medicationService;

    @QueryMapping
    public List<MedicationResponse> medications(@Argument int page, @Argument int size) {
        return medicationService.getMedications(PageRequest.of(page, size)).getContent();
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
