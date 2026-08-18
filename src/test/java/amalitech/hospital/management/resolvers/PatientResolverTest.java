package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link PatientResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. */
@GraphQlTest(PatientResolver.class)
@Import(GraphQlConfig.class)
class PatientResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private PatientService patientService;

    private PatientResponse existingPatient() {
        PatientResponse response = new PatientResponse();
        response.setPatientId("patient-1");
        response.setFirstName("Alice");
        response.setLastName("Doe");
        response.setDob(LocalDate.of(1990, 1, 1));
        response.setGender("F");
        response.setStatus("active");
        return response;
    }

    @Test
    void patient_returnsMappedResponse() {
        when(patientService.getPatient("patient-1")).thenReturn(existingPatient());

        graphQlTester.document("{ patient(patientId: \"patient-1\") { firstName lastName dob status } }")
                .execute()
                .path("patient.firstName").entity(String.class).isEqualTo("Alice")
                .path("patient.dob").entity(String.class).isEqualTo("1990-01-01");

        verify(patientService).getPatient("patient-1");
    }

    @Test
    void patients_returnsPagedContent() {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(patientService.getPatients(any(), any(), any()))
                .thenReturn(new PagedModel<>(new PageImpl<>(List.of(existingPatient()), pageable, 1)));

        graphQlTester.document("{ patients(page: 0, size: 20) { patientId } }")
                .execute()
                .path("patients[0].patientId").entity(String.class).isEqualTo("patient-1");

        verify(patientService).getPatients(any(), any(), any());
    }

    @Test
    void createPatient_delegatesToService() {
        when(patientService.createPatient(any())).thenReturn(existingPatient());

        graphQlTester.document(
                        "mutation { createPatient(input: { firstName: \"Alice\", lastName: \"Doe\", dob: \"1990-01-01\", gender: \"F\" }) { patientId } }")
                .execute()
                .path("createPatient.patientId").entity(String.class).isEqualTo("patient-1");

        verify(patientService).createPatient(any());
    }

    @Test
    void deletePatient_returnsTrue() {
        graphQlTester.document("mutation { deletePatient(patientId: \"patient-1\") }")
                .execute()
                .path("deletePatient").entity(Boolean.class).isEqualTo(true);

        verify(patientService).deletePatient("patient-1");
    }
}
