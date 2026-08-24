package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.lab.LabResultRequest;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.service.LabResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link LabResultService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class LabResultResolver {

    private final LabResultService labResultService;

    @QueryMapping
    public LabResultResponse labResult(@Argument String labOrderId) {
        return labResultService.getResult(labOrderId);
    }

    @MutationMapping
    public LabResultResponse createLabResult(@Argument String labOrderId, @Argument @Valid LabResultRequest input) {
        return labResultService.createResult(labOrderId, input);
    }

    @MutationMapping
    public LabResultResponse updateLabResult(@Argument String labOrderId, @Argument @Valid LabResultRequest input) {
        return labResultService.updateResult(labOrderId, input);
    }

    @MutationMapping
    public LabResultResponse patchLabResult(@Argument String labOrderId, @Argument @Valid LabResultRequest input) {
        return labResultService.patchResult(labOrderId, input);
    }

    @MutationMapping
    public boolean deleteLabResult(@Argument String labOrderId) {
        labResultService.deleteResult(labOrderId);
        return true;
    }
}
