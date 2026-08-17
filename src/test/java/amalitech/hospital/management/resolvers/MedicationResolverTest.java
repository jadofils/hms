package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link MedicationResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. Also exercises the {@code BigDecimal} scalar registered by
 *  {@link GraphQlConfig}. */
@GraphQlTest(MedicationResolver.class)
@Import(GraphQlConfig.class)
class MedicationResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private MedicationService medicationService;

    private MedicationResponse existingMedication() {
        MedicationResponse response = new MedicationResponse();
        response.setMedicationId("med-1");
        response.setName("Paracetamol");
        response.setUnitPrice(new BigDecimal("5.50"));
        return response;
    }

    @Test
    void medication_returnsMappedResponse() {
        when(medicationService.getMedication("med-1")).thenReturn(existingMedication());

        graphQlTester.document("{ medication(medicationId: \"med-1\") { name unitPrice } }")
                .execute()
                .path("medication.name").entity(String.class).isEqualTo("Paracetamol")
                .path("medication.unitPrice").entity(BigDecimal.class).isEqualTo(new BigDecimal("5.50"));
    }

    @Test
    void createMedication_delegatesToService() {
        when(medicationService.createMedication(any())).thenReturn(existingMedication());

        graphQlTester.document("mutation { createMedication(input: { name: \"Paracetamol\" }) { medicationId } }")
                .execute()
                .path("createMedication.medicationId").entity(String.class).isEqualTo("med-1");
    }

    @Test
    void deleteMedication_returnsTrue() {
        graphQlTester.document("mutation { deleteMedication(medicationId: \"med-1\") }")
                .execute()
                .path("deleteMedication").entity(Boolean.class).isEqualTo(true);

        verify(medicationService).deleteMedication("med-1");
    }
}
