package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.patient.PatientRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.PatientService;
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
 * GraphQL front door for {@link PatientService} — see {@code UserResolver}'s Javadoc for
 * the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PatientResolver {

    private final PatientService patientService;

    @QueryMapping
    public List<PatientResponse> patients(@Argument int page, @Argument int size,
            @Argument String status, @Argument String gender) {
        return patientService.getPatients(PageRequest.of(page, size), status, gender).getContent();
    }

    @QueryMapping
    public PatientResponse patient(@Argument String patientId) {
        return patientService.getPatient(patientId);
    }

    @MutationMapping
    public PatientResponse createPatient(@Argument @Valid PatientRequest input) {
        return patientService.createPatient(input);
    }

    @MutationMapping
    public PatientResponse updatePatient(@Argument String patientId, @Argument @Valid PatientRequest input) {
        return patientService.updatePatient(patientId, input);
    }

    @MutationMapping
    public boolean deletePatient(@Argument String patientId) {
        patientService.deletePatient(patientId);
        return true;
    }
}
