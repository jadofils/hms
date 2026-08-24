package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.PatchMedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import amalitech.hospital.management.utils.PageableDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Medication catalog CRUD.
 *
 * Single-item lookups are cached in Redis under the "medications" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;

    /** {@code lowStock=true} narrows the catalog to medications needing reorder across
     *  any of their own batches — see {@link MedicationRepository#findLowStock}'s own
     *  Javadoc for how this differs from {@code MedicalInventoryService}'s own
     *  batch-level {@code lowStock} filter. Omitted, this is the full catalog. */
    public PagedModel<MedicationResponse> getMedications(Pageable pageable, Boolean lowStock) {
        // Defaults to name ASC (matching this endpoint's own Swagger sort example)
        // when the caller sends no ?sort= at all — see PageableDefaults' own Javadoc.
        Pageable sorted = PageableDefaults.withDefaultSort(pageable, "name", Sort.Direction.ASC);
        if (Boolean.TRUE.equals(lowStock)) {
            return new PagedModel<>(medicationRepository.findLowStock(sorted).map(this::toResponse));
        }
        return new PagedModel<>(medicationRepository.findAll(sorted).map(this::toResponse));
    }

    @Cacheable(value = "medications", key = "#medicationId")
    public MedicationResponse getMedication(String medicationId) {
        return toResponse(findMedicationOrThrow(medicationId));
    }

    @Transactional
    public MedicationResponse createMedication(MedicationRequest request) {
        if (medicationRepository.existsByName(request.getName())) {
            throw new ConflictException("Medication '" + request.getName() + "' already exists");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Medication medication = new Medication();
        medication.setName(request.getName());
        medication.setGenericName(request.getGenericName());
        medication.setForm(request.getForm());
        medication.setUnitPrice(request.getUnitPrice());
        medication.setCreatedAt(now);
        medication.setUpdatedAt(now);
        return toResponse(medicationRepository.save(medication));
    }

    @Transactional
    @CachePut(value = "medications", key = "#medicationId")
    public MedicationResponse updateMedication(String medicationId, MedicationRequest request) {
        Medication medication = findMedicationOrThrow(medicationId);
        if (!medication.getName().equals(request.getName())
                && medicationRepository.existsByName(request.getName())) {
            throw new ConflictException("Medication '" + request.getName() + "' already exists");
        }
        medication.setName(request.getName());
        medication.setGenericName(request.getGenericName());
        medication.setForm(request.getForm());
        medication.setUnitPrice(request.getUnitPrice());
        medication.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(medicationRepository.save(medication));
    }

    /** Partial-update counterpart to {@link #updateMedication} — only touches a field
     *  when the request actually included it. */
    @Transactional
    @CachePut(value = "medications", key = "#medicationId")
    public MedicationResponse patchMedication(String medicationId, PatchMedicationRequest patch) {
        Medication medication = findMedicationOrThrow(medicationId);
        if (patch.getName() != null) {
            if (!medication.getName().equals(patch.getName()) && medicationRepository.existsByName(patch.getName())) {
                throw new ConflictException("Medication '" + patch.getName() + "' already exists");
            }
            medication.setName(patch.getName());
        }
        if (patch.getGenericName() != null) {
            medication.setGenericName(patch.getGenericName());
        }
        if (patch.getForm() != null) {
            medication.setForm(patch.getForm());
        }
        if (patch.getUnitPrice() != null) {
            medication.setUnitPrice(patch.getUnitPrice());
        }
        medication.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(medicationRepository.save(medication));
    }

    @Transactional
    @CacheEvict(value = "medications", key = "#medicationId")
    public void deleteMedication(String medicationId) {
        Medication medication = findMedicationOrThrow(medicationId);
        medication.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        medicationRepository.save(medication);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Medication findMedicationOrThrow(String medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new NotFoundException("Medication not found: " + medicationId));
        if (medication.getDeletedAt() != null) {
            throw new NotFoundException("Medication not found: " + medicationId);
        }
        return medication;
    }

    private MedicationResponse toResponse(Medication medication) {
        MedicationResponse response = new MedicationResponse();
        response.setMedicationId(medication.getMedicationId());
        response.setName(medication.getName());
        response.setGenericName(medication.getGenericName());
        response.setForm(medication.getForm());
        response.setUnitPrice(medication.getUnitPrice());
        return response;
    }
}
