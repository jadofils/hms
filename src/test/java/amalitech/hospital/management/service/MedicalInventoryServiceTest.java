package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.MedicalInventoryRequest;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.MedicalInventory;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.repository.pharmacy.MedicalInventoryRepository;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalInventoryServiceTest {

    @Mock private MedicalInventoryRepository medicalInventoryRepository;
    @Mock private MedicationRepository medicationRepository;

    private MedicalInventoryService medicalInventoryService;

    private Medication existingMedication;
    private MedicalInventory existingInventory;

    @BeforeEach
    void setUp() {
        medicalInventoryService = new MedicalInventoryService(medicalInventoryRepository, medicationRepository);

        existingMedication = new Medication();
        existingMedication.setMedicationId("med-1");
        existingMedication.setName("Amoxicillin");

        existingInventory = new MedicalInventory();
        existingInventory.setInventoryId("inv-1");
        existingInventory.setMedication(existingMedication);
        existingInventory.setBatchNumber("B1");
        existingInventory.setExpiryDate(LocalDate.now().plusYears(1));
        existingInventory.setQuantityInStock(20);
        existingInventory.setReorderLevel(5);
    }

    @Test
    void getInventoryRecord_returnsMappedResponse_whenFoundAndActive() {
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));

        MedicalInventoryResponse response = medicalInventoryService.getInventoryRecord("inv-1");

        assertThat(response.getInventoryId()).isEqualTo("inv-1");
        assertThat(response.getMedicationId()).isEqualTo("med-1");
        assertThat(response.getMedicationName()).isEqualTo("Amoxicillin");
    }

    @Test
    void getInventoryRecord_throwsNotFound_whenAbsent() {
        when(medicalInventoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalInventoryService.getInventoryRecord("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getInventoryRecord_throwsNotFound_whenSoftDeleted() {
        existingInventory.setDeletedAt(LocalDateTime.now());
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));

        assertThatThrownBy(() -> medicalInventoryService.getInventoryRecord("inv-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInventoryRecord_throwsNotFound_whenMedicationAbsent() {
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());
        MedicalInventoryRequest request = requestFor("missing");

        assertThatThrownBy(() -> medicalInventoryService.createInventoryRecord(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInventoryRecord_throwsNotFound_whenMedicationSoftDeleted() {
        existingMedication.setDeletedAt(LocalDateTime.now());
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        MedicalInventoryRequest request = requestFor("med-1");

        assertThatThrownBy(() -> medicalInventoryService.createInventoryRecord(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInventoryRecord_appliesDefaults_whenQuantityAndReorderLevelOmitted() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicalInventoryRepository.save(any(MedicalInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        MedicalInventoryRequest request = requestFor("med-1");

        MedicalInventoryResponse response = medicalInventoryService.createInventoryRecord(request);

        assertThat(response.getQuantityInStock()).isEqualTo(0);
        assertThat(response.getReorderLevel()).isEqualTo(10);
    }

    @Test
    void updateInventoryRecord_throwsNotFound_whenAbsent() {
        when(medicalInventoryRepository.findById("missing")).thenReturn(Optional.empty());
        MedicalInventoryRequest request = requestFor("med-1");

        assertThatThrownBy(() -> medicalInventoryService.updateInventoryRecord("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateInventoryRecord_updatesFields() {
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicalInventoryRepository.save(any(MedicalInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        MedicalInventoryRequest request = requestFor("med-1");
        request.setQuantityInStock(99);

        MedicalInventoryResponse response = medicalInventoryService.updateInventoryRecord("inv-1", request);

        assertThat(response.getQuantityInStock()).isEqualTo(99);
    }

    @Test
    void deleteInventoryRecord_setsDeletedAt() {
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));
        when(medicalInventoryRepository.save(any(MedicalInventory.class))).thenAnswer(inv -> inv.getArgument(0));

        medicalInventoryService.deleteInventoryRecord("inv-1");

        assertThat(existingInventory.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteInventoryRecord_throwsNotFound_whenAbsent() {
        when(medicalInventoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalInventoryService.deleteInventoryRecord("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static MedicalInventoryRequest requestFor(String medicationId) {
        MedicalInventoryRequest request = new MedicalInventoryRequest();
        request.setMedicationId(medicationId);
        request.setExpiryDate(LocalDate.now().plusYears(1));
        return request;
    }
}
