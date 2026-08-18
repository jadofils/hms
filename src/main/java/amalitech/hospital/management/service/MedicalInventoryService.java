package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.MedicalInventoryRequest;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.MedicalInventory;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.repository.pharmacy.MedicalInventoryRepository;
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
import java.time.ZoneId;

/**
 * Medication stock CRUD — each record belongs to one {@link Medication}.
 *
 * Single-item lookups are cached in Redis under the "medical-inventory" cache; every
 * write invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class MedicalInventoryService {

    private final MedicalInventoryRepository medicalInventoryRepository;
    private final MedicationRepository medicationRepository;

    public PagedModel<MedicalInventoryResponse> getInventoryRecords(Pageable pageable) {
        return new PagedModel<>(medicalInventoryRepository.findAll(pageable).map(this::toResponse));
    }

    @Cacheable(value = "medical-inventory", key = "#inventoryId")
    public MedicalInventoryResponse getInventoryRecord(String inventoryId) {
        return toResponse(findInventoryOrThrow(inventoryId));
    }

    @Transactional
    public MedicalInventoryResponse createInventoryRecord(MedicalInventoryRequest request) {
        Medication medication = findMedicationOrThrow(request.getMedicationId());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        MedicalInventory inventory = new MedicalInventory();
        inventory.setMedication(medication);
        inventory.setBatchNumber(request.getBatchNumber());
        inventory.setExpiryDate(request.getExpiryDate());
        inventory.setQuantityInStock(request.getQuantityInStock() == null ? 0 : request.getQuantityInStock());
        inventory.setReorderLevel(request.getReorderLevel() == null ? 10 : request.getReorderLevel());
        inventory.setSupplier(request.getSupplier());
        inventory.setCreatedAt(now);
        inventory.setUpdatedAt(now);
        return toResponse(medicalInventoryRepository.save(inventory));
    }

    @Transactional
    @CachePut(value = "medical-inventory", key = "#inventoryId")
    public MedicalInventoryResponse updateInventoryRecord(String inventoryId, MedicalInventoryRequest request) {
        MedicalInventory inventory = findInventoryOrThrow(inventoryId);
        Medication medication = findMedicationOrThrow(request.getMedicationId());

        inventory.setMedication(medication);
        inventory.setBatchNumber(request.getBatchNumber());
        inventory.setExpiryDate(request.getExpiryDate());
        inventory.setQuantityInStock(request.getQuantityInStock() == null ? 0 : request.getQuantityInStock());
        inventory.setReorderLevel(request.getReorderLevel() == null ? 10 : request.getReorderLevel());
        inventory.setSupplier(request.getSupplier());
        inventory.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(medicalInventoryRepository.save(inventory));
    }

    @Transactional
    @CacheEvict(value = "medical-inventory", key = "#inventoryId")
    public void deleteInventoryRecord(String inventoryId) {
        MedicalInventory inventory = findInventoryOrThrow(inventoryId);
        inventory.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        medicalInventoryRepository.save(inventory);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MedicalInventory findInventoryOrThrow(String inventoryId) {
        MedicalInventory inventory = medicalInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException("Inventory record not found: " + inventoryId));
        if (inventory.getDeletedAt() != null) {
            throw new NotFoundException("Inventory record not found: " + inventoryId);
        }
        return inventory;
    }

    private Medication findMedicationOrThrow(String medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new NotFoundException("Medication not found: " + medicationId));
        if (medication.getDeletedAt() != null) {
            throw new NotFoundException("Medication not found: " + medicationId);
        }
        return medication;
    }

    private MedicalInventoryResponse toResponse(MedicalInventory inventory) {
        MedicalInventoryResponse response = new MedicalInventoryResponse();
        response.setInventoryId(inventory.getInventoryId());
        response.setMedicationId(inventory.getMedication().getMedicationId());
        response.setMedicationName(inventory.getMedication().getName());
        response.setBatchNumber(inventory.getBatchNumber());
        response.setExpiryDate(inventory.getExpiryDate());
        response.setQuantityInStock(inventory.getQuantityInStock());
        response.setReorderLevel(inventory.getReorderLevel());
        response.setSupplier(inventory.getSupplier());
        return response;
    }
}
