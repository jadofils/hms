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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void getInventoryRecords_returnsEveryRow_whenNoFilterGiven() {
        when(medicalInventoryRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingInventory)));

        PagedModel<MedicalInventoryResponse> result =
                medicalInventoryService.getInventoryRecords(PageRequest.of(0, 20), null, null);

        assertThat(result.getContent()).hasSize(1);
        verify(medicalInventoryRepository, never()).findLowStock(any());
    }

    @Test
    void getInventoryRecords_filtersByLowStock_whenLowStockTrue() {
        // any(Pageable.class), not the literal instance passed in below —
        // getInventoryRecords now applies a default sort (see PageableDefaults) when
        // the caller sends none, so the Pageable that actually reaches the repository
        // is a different (sorted) instance.
        when(medicalInventoryRepository.findLowStock(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingInventory)));

        PagedModel<MedicalInventoryResponse> result =
                medicalInventoryService.getInventoryRecords(PageRequest.of(0, 20), "med-1", true);

        // lowStock=true wins even though medicationId was also given.
        assertThat(result.getContent()).hasSize(1);
        verify(medicalInventoryRepository, never()).findByMedication_MedicationIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void getInventoryRecords_filtersByMedicationId_whenOnlyMedicationIdGiven() {
        when(medicalInventoryRepository.findByMedication_MedicationIdAndDeletedAtIsNull(eq("med-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingInventory)));

        PagedModel<MedicalInventoryResponse> result =
                medicalInventoryService.getInventoryRecords(PageRequest.of(0, 20), "med-1", null);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getInventoryRecords_defaultsToExpiryDateAscending_whenCallerSendsNoSort() {
        when(medicalInventoryRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingInventory)));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        medicalInventoryService.getInventoryRecords(PageRequest.of(0, 20), null, null);

        verify(medicalInventoryRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "expiryDate"));
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

    // ── patchInventoryRecord ─────────────────────────────────────────────────

    @Test
    void patchInventoryRecord_changesOnlyQuantity_whenOnlyQuantityGiven() {
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));
        when(medicalInventoryRepository.save(any(MedicalInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        amalitech.hospital.management.dto.pharmacy.PatchMedicalInventoryRequest patch =
                new amalitech.hospital.management.dto.pharmacy.PatchMedicalInventoryRequest();
        patch.setQuantityInStock(99);

        MedicalInventoryResponse response = medicalInventoryService.patchInventoryRecord("inv-1", patch);

        assertThat(response.getQuantityInStock()).isEqualTo(99);
        assertThat(response.getReorderLevel()).isEqualTo(5); // untouched, not reset to the create-time default
        assertThat(response.getBatchNumber()).isEqualTo("B1"); // untouched
        verify(medicationRepository, never()).findById(any());
    }

    @Test
    void patchInventoryRecord_throwsNotFound_whenMedicationIdGivenButAbsent() {
        when(medicalInventoryRepository.findById("inv-1")).thenReturn(Optional.of(existingInventory));
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());
        amalitech.hospital.management.dto.pharmacy.PatchMedicalInventoryRequest patch =
                new amalitech.hospital.management.dto.pharmacy.PatchMedicalInventoryRequest();
        patch.setMedicationId("missing");

        assertThatThrownBy(() -> medicalInventoryService.patchInventoryRecord("inv-1", patch))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patchInventoryRecord_throwsNotFound_whenAbsent() {
        when(medicalInventoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalInventoryService.patchInventoryRecord("missing",
                new amalitech.hospital.management.dto.pharmacy.PatchMedicalInventoryRequest()))
                .isInstanceOf(NotFoundException.class);
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
