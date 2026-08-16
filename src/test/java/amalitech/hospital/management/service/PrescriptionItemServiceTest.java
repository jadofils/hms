package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.PrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.model.pharmacy.Prescription;
import amalitech.hospital.management.model.pharmacy.PrescriptionItem;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionItemRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionItemServiceTest {

    @Mock private PrescriptionItemRepository prescriptionItemRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicationRepository medicationRepository;

    private PrescriptionItemService prescriptionItemService;

    private Prescription existingPrescription;
    private Medication existingMedication;
    private PrescriptionItem existingItem;

    @BeforeEach
    void setUp() {
        prescriptionItemService = new PrescriptionItemService(
                prescriptionItemRepository, prescriptionRepository, medicationRepository);

        existingPrescription = new Prescription();
        existingPrescription.setPrescriptionId("presc-1");

        existingMedication = new Medication();
        existingMedication.setMedicationId("med-1");
        existingMedication.setName("Amoxicillin");

        existingItem = new PrescriptionItem();
        existingItem.setItemId("item-1");
        existingItem.setPrescription(existingPrescription);
        existingItem.setMedication(existingMedication);
        existingItem.setDosage("500mg");
        existingItem.setQuantity(10);
    }

    @Test
    void getItems_throwsNotFound_whenPrescriptionAbsent() {
        when(prescriptionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionItemService.getItems("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getItems_returnsMappedResponses() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(prescriptionItemRepository.findByPrescription_PrescriptionIdAndDeletedAtIsNull("presc-1"))
                .thenReturn(List.of(existingItem));

        List<PrescriptionItemResponse> result = prescriptionItemService.getItems("presc-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMedicationName()).isEqualTo("Amoxicillin");
    }

    @Test
    void createItem_throwsNotFound_whenPrescriptionAbsent() {
        when(prescriptionRepository.findById("missing")).thenReturn(Optional.empty());
        PrescriptionItemRequest request = requestFor("med-1");

        assertThatThrownBy(() -> prescriptionItemService.createItem("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createItem_throwsNotFound_whenMedicationAbsent() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());
        PrescriptionItemRequest request = requestFor("missing");

        assertThatThrownBy(() -> prescriptionItemService.createItem("presc-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createItem_throwsNotFound_whenPrescriptionSoftDeleted() {
        existingPrescription.setDeletedAt(java.time.LocalDateTime.now());
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        PrescriptionItemRequest request = requestFor("med-1");

        assertThatThrownBy(() -> prescriptionItemService.createItem("presc-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createItem_throwsNotFound_whenMedicationSoftDeleted() {
        existingMedication.setDeletedAt(java.time.LocalDateTime.now());
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        PrescriptionItemRequest request = requestFor("med-1");

        assertThatThrownBy(() -> prescriptionItemService.createItem("presc-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createItem_savesSuccessfully() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        PrescriptionItemRequest request = requestFor("med-1");

        PrescriptionItemResponse response = prescriptionItemService.createItem("presc-1", request);

        assertThat(response.getPrescriptionId()).isEqualTo("presc-1");
        assertThat(response.getMedicationId()).isEqualTo("med-1");
    }

    @Test
    void updateItem_throwsNotFound_whenItemBelongsToDifferentPrescription() {
        when(prescriptionItemRepository.findById("item-1")).thenReturn(Optional.of(existingItem));
        PrescriptionItemRequest request = requestFor("med-1");

        assertThatThrownBy(() -> prescriptionItemService.updateItem("other-presc", "item-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateItem_updatesFields() {
        when(prescriptionItemRepository.findById("item-1")).thenReturn(Optional.of(existingItem));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        PrescriptionItemRequest request = requestFor("med-1");
        request.setQuantity(20);

        PrescriptionItemResponse response = prescriptionItemService.updateItem("presc-1", "item-1", request);

        assertThat(response.getQuantity()).isEqualTo(20);
    }

    @Test
    void deleteItem_setsDeletedAt() {
        when(prescriptionItemRepository.findById("item-1")).thenReturn(Optional.of(existingItem));
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(inv -> inv.getArgument(0));

        prescriptionItemService.deleteItem("presc-1", "item-1");

        assertThat(existingItem.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteItem_throwsNotFound_whenAbsent() {
        when(prescriptionItemRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionItemService.deleteItem("presc-1", "missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static PrescriptionItemRequest requestFor(String medicationId) {
        PrescriptionItemRequest request = new PrescriptionItemRequest();
        request.setMedicationId(medicationId);
        request.setQuantity(1);
        return request;
    }
}
