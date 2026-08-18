package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.service.MedicationService;
import amalitech.hospital.management.service.PrescriptionItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link PrescriptionItemResolver} — see {@code UserResolverTest}'s
 *  Javadoc for the shared reasoning. */
@GraphQlTest(PrescriptionItemResolver.class)
@Import(GraphQlConfig.class)
class PrescriptionItemResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private PrescriptionItemService prescriptionItemService;
    @MockitoBean
    private MedicationService medicationService;

    private PrescriptionItemResponse existingItem() {
        PrescriptionItemResponse response = new PrescriptionItemResponse();
        response.setItemId("item-1");
        response.setPrescriptionId("presc-1");
        response.setMedicationId("med-1");
        response.setQuantity(2);
        return response;
    }

    @Test
    void prescriptionItems_returnsRealMedicationObject() {
        when(prescriptionItemService.getItems("presc-1")).thenReturn(List.of(existingItem()));
        MedicationResponse medication = new MedicationResponse();
        medication.setMedicationId("med-1");
        medication.setName("Paracetamol");
        when(medicationService.getMedication("med-1")).thenReturn(medication);

        graphQlTester.document("{ prescriptionItems(prescriptionId: \"presc-1\") { quantity medication { name } } }")
                .execute()
                .path("prescriptionItems[0].medication.name").entity(String.class).isEqualTo("Paracetamol");

        verify(prescriptionItemService).getItems("presc-1");
    }

    @Test
    void createPrescriptionItem_delegatesToService() {
        when(prescriptionItemService.createItem(eq("presc-1"), any())).thenReturn(existingItem());

        graphQlTester.document(
                        "mutation { createPrescriptionItem(prescriptionId: \"presc-1\", input: { medicationId: \"med-1\", quantity: 2 }) { itemId } }")
                .execute()
                .path("createPrescriptionItem.itemId").entity(String.class).isEqualTo("item-1");

        verify(prescriptionItemService).createItem(eq("presc-1"), any());
    }

    @Test
    void deletePrescriptionItem_returnsTrue() {
        graphQlTester.document("mutation { deletePrescriptionItem(prescriptionId: \"presc-1\", itemId: \"item-1\") }")
                .execute()
                .path("deletePrescriptionItem").entity(Boolean.class).isEqualTo(true);

        verify(prescriptionItemService).deleteItem("presc-1", "item-1");
    }
}
