package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    @Mock private MedicationRepository medicationRepository;

    private MedicationService medicationService;

    private Medication existingMedication;

    @BeforeEach
    void setUp() {
        medicationService = new MedicationService(medicationRepository);

        existingMedication = new Medication();
        existingMedication.setMedicationId("med-1");
        existingMedication.setName("Amoxicillin");
        existingMedication.setGenericName("Amoxicillin Trihydrate");
        existingMedication.setForm("capsule");
        existingMedication.setUnitPrice(new BigDecimal("2.50"));
    }

    @Test
    void getMedications_returnsEveryRow_whenLowStockNotRequested() {
        when(medicationRepository.findAll(any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(existingMedication)));

        var result = medicationService.getMedications(
                org.springframework.data.domain.PageRequest.of(0, 20), null);

        assertThat(result.getContent()).hasSize(1);
        verify(medicationRepository, never()).findLowStock(any());
    }

    @Test
    void getMedications_filtersByLowStock_whenLowStockTrue() {
        // any(Pageable.class), not the literal instance passed in below —
        // getMedications now applies a default sort (see PageableDefaults) when the
        // caller sends none, so the Pageable that actually reaches the repository is a
        // different (sorted) instance.
        when(medicationRepository.findLowStock(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(existingMedication)));

        var result = medicationService.getMedications(org.springframework.data.domain.PageRequest.of(0, 20), true);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Amoxicillin");
    }

    @Test
    void getMedications_defaultsToNameAscending_whenCallerSendsNoSort() {
        when(medicationRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(existingMedication)));
        ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        medicationService.getMedications(org.springframework.data.domain.PageRequest.of(0, 20), null);

        verify(medicationRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort())
                .isEqualTo(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name"));
    }

    @Test
    void getMedication_returnsMappedResponse_whenFoundAndActive() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));

        MedicationResponse response = medicationService.getMedication("med-1");

        assertThat(response.getMedicationId()).isEqualTo("med-1");
        assertThat(response.getName()).isEqualTo("Amoxicillin");
    }

    @Test
    void getMedication_throwsNotFound_whenAbsent() {
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicationService.getMedication("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMedication_throwsNotFound_whenSoftDeleted() {
        existingMedication.setDeletedAt(LocalDateTime.now());
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));

        assertThatThrownBy(() -> medicationService.getMedication("med-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createMedication_throwsConflict_whenNameTaken() {
        MedicationRequest request = requestFor("Amoxicillin");
        when(medicationRepository.existsByName("Amoxicillin")).thenReturn(true);

        assertThatThrownBy(() -> medicationService.createMedication(request))
                .isInstanceOf(ConflictException.class);
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void createMedication_savesSuccessfully() {
        MedicationRequest request = requestFor("Ibuprofen");
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicationResponse response = medicationService.createMedication(request);

        ArgumentCaptor<Medication> captor = ArgumentCaptor.forClass(Medication.class);
        verify(medicationRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(response.getName()).isEqualTo("Ibuprofen");
    }

    @Test
    void updateMedication_throwsNotFound_whenAbsent() {
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());
        MedicationRequest request = requestFor("Amoxicillin");

        assertThatThrownBy(() -> medicationService.updateMedication("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateMedication_doesNotConflictCheck_whenNameUnchanged() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));
        MedicationRequest request = requestFor("Amoxicillin");

        medicationService.updateMedication("med-1", request);

        verify(medicationRepository, never()).existsByName("Amoxicillin");
    }

    @Test
    void updateMedication_throwsConflict_whenRenamedToExistingName() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.existsByName("Paracetamol")).thenReturn(true);
        MedicationRequest request = requestFor("Paracetamol");

        assertThatThrownBy(() -> medicationService.updateMedication("med-1", request))
                .isInstanceOf(ConflictException.class);
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void updateMedication_updatesFields() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.existsByName("Paracetamol")).thenReturn(false);
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));
        MedicationRequest request = requestFor("Paracetamol");

        MedicationResponse response = medicationService.updateMedication("med-1", request);

        assertThat(response.getName()).isEqualTo("Paracetamol");
    }

    // ── patchMedication ──────────────────────────────────────────────────────

    @Test
    void patchMedication_changesOnlyForm_whenOnlyFormGiven() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));
        amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest patch =
                new amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest();
        patch.setForm("syrup");

        MedicationResponse response = medicationService.patchMedication("med-1", patch);

        assertThat(response.getForm()).isEqualTo("syrup");
        assertThat(response.getName()).isEqualTo("Amoxicillin"); // untouched
        verify(medicationRepository, never()).existsByName(anyString());
    }

    @Test
    void patchMedication_throwsConflict_whenRenamedToExistingName() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.existsByName("Paracetamol")).thenReturn(true);
        amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest patch =
                new amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest();
        patch.setName("Paracetamol");

        assertThatThrownBy(() -> medicationService.patchMedication("med-1", patch))
                .isInstanceOf(ConflictException.class);
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void patchMedication_throwsNotFound_whenAbsent() {
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());
        amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest patch =
                new amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest();
        patch.setName("Amoxicillin");

        assertThatThrownBy(() -> medicationService.patchMedication("missing", patch))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteMedication_setsDeletedAt() {
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(existingMedication));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));

        medicationService.deleteMedication("med-1");

        assertThat(existingMedication.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteMedication_throwsNotFound_whenAbsent() {
        when(medicationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicationService.deleteMedication("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static MedicationRequest requestFor(String name) {
        MedicationRequest request = new MedicationRequest();
        request.setName(name);
        request.setForm("tablet");
        request.setUnitPrice(new BigDecimal("1.00"));
        return request;
    }
}
