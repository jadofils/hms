package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    public PagedModel<MedicationResponse> getMedications(Pageable pageable) {
        return new PagedModel<>(medicationRepository.findAll(pageable).map(this::toResponse));
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
        LocalDateTime now = LocalDateTime.now();
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
        medication.setUpdatedAt(LocalDateTime.now());
        return toResponse(medicationRepository.save(medication));
    }

    @Transactional
    @CacheEvict(value = "medications", key = "#medicationId")
    public void deleteMedication(String medicationId) {
        Medication medication = findMedicationOrThrow(medicationId);
        medication.setDeletedAt(LocalDateTime.now());
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
