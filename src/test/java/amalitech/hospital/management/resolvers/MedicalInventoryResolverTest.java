package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicalInventoryService;
import amalitech.hospital.management.service.MedicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link MedicalInventoryResolver} — see {@code UserResolverTest}'s
 *  Javadoc for the shared reasoning. */
@GraphQlTest(MedicalInventoryResolver.class)
@Import(GraphQlConfig.class)
class MedicalInventoryResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private MedicalInventoryService medicalInventoryService;
    @MockitoBean
    private MedicationService medicationService;

    private MedicalInventoryResponse existingInventory() {
        MedicalInventoryResponse response = new MedicalInventoryResponse();
        response.setInventoryId("inv-1");
        response.setMedicationId("med-1");
        response.setExpiryDate(LocalDate.of(2030, 1, 1));
        response.setQuantityInStock(100);
        response.setReorderLevel(10);
        return response;
    }

    @Test
    void inventoryRecord_returnsRealMedicationObject() {
        when(medicalInventoryService.getInventoryRecord("inv-1")).thenReturn(existingInventory());
        MedicationResponse medication = new MedicationResponse();
        medication.setMedicationId("med-1");
        medication.setName("Paracetamol");
        when(medicationService.getMedication("med-1")).thenReturn(medication);

        graphQlTester.document("{ inventoryRecord(inventoryId: \"inv-1\") { expiryDate quantityInStock medication { name } } }")
                .execute()
                .path("inventoryRecord.expiryDate").entity(String.class).isEqualTo("2030-01-01")
                .path("inventoryRecord.medication.name").entity(String.class).isEqualTo("Paracetamol");

        verify(medicalInventoryService).getInventoryRecord("inv-1");
    }

    @Test
    void createInventoryRecord_delegatesToService() {
        when(medicalInventoryService.createInventoryRecord(any())).thenReturn(existingInventory());

        graphQlTester.document(
                        "mutation { createInventoryRecord(input: { medicationId: \"med-1\", expiryDate: \"2030-01-01\" }) { inventoryId } }")
                .execute()
                .path("createInventoryRecord.inventoryId").entity(String.class).isEqualTo("inv-1");

        verify(medicalInventoryService).createInventoryRecord(any());
    }

    @Test
    void deleteInventoryRecord_returnsTrue() {
        graphQlTester.document("mutation { deleteInventoryRecord(inventoryId: \"inv-1\") }")
                .execute()
                .path("deleteInventoryRecord").entity(Boolean.class).isEqualTo(true);

        verify(medicalInventoryService).deleteInventoryRecord("inv-1");
    }
}
