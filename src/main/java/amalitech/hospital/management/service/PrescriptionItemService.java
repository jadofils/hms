package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.pharmacy.PatchPrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.pharmacy.Medication;
import amalitech.hospital.management.model.pharmacy.Prescription;
import amalitech.hospital.management.model.pharmacy.PrescriptionItem;
import amalitech.hospital.management.repository.pharmacy.MedicationRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionItemRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Prescription line items. Scoped under a {@code prescriptionId} — every operation
 * checks the prescription exists first, same shape as {@code DoctorScheduleService}
 * being scoped under a {@code doctorId}.
 */
@Service
@RequiredArgsConstructor
public class PrescriptionItemService {

    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicationRepository medicationRepository;

    public List<PrescriptionItemResponse> getItems(String prescriptionId) {
        findPrescriptionOrThrow(prescriptionId);
        return prescriptionItemRepository.findByPrescription_PrescriptionIdAndDeletedAtIsNull(prescriptionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PrescriptionItemResponse createItem(String prescriptionId, PrescriptionItemRequest request) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        Medication medication = findMedicationOrThrow(request.getMedicationId());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        PrescriptionItem item = new PrescriptionItem();
        item.setPrescription(prescription);
        item.setMedication(medication);
        item.setDosage(request.getDosage());
        item.setQuantity(request.getQuantity());
        item.setInstructions(request.getInstructions());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return toResponse(prescriptionItemRepository.save(item));
    }

    @Transactional
    public PrescriptionItemResponse updateItem(String prescriptionId, String itemId, PrescriptionItemRequest request) {
        PrescriptionItem item = findItemOrThrow(prescriptionId, itemId);
        Medication medication = findMedicationOrThrow(request.getMedicationId());

        item.setMedication(medication);
        item.setDosage(request.getDosage());
        item.setQuantity(request.getQuantity());
        item.setInstructions(request.getInstructions());
        item.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(prescriptionItemRepository.save(item));
    }

    /**
     * Partial-update counterpart to {@link #updateItem} — only the fields actually
     * present in {@code patch} are changed; everything else on the existing item is
     * left untouched.
     */
    @Transactional
    public PrescriptionItemResponse patchItem(String prescriptionId, String itemId, PatchPrescriptionItemRequest patch) {
        PrescriptionItem item = findItemOrThrow(prescriptionId, itemId);
        if (patch.getMedicationId() != null) {
            item.setMedication(findMedicationOrThrow(patch.getMedicationId()));
        }
        if (patch.getDosage() != null) {
            item.setDosage(patch.getDosage());
        }
        if (patch.getQuantity() != null) {
            item.setQuantity(patch.getQuantity());
        }
        if (patch.getInstructions() != null) {
            item.setInstructions(patch.getInstructions());
        }
        item.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(prescriptionItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(String prescriptionId, String itemId) {
        PrescriptionItem item = findItemOrThrow(prescriptionId, itemId);
        item.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        prescriptionItemRepository.save(item);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Prescription findPrescriptionOrThrow(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Prescription not found: " + prescriptionId));
        if (prescription.getDeletedAt() != null) {
            throw new NotFoundException("Prescription not found: " + prescriptionId);
        }
        return prescription;
    }

    private Medication findMedicationOrThrow(String medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new NotFoundException("Medication not found: " + medicationId));
        if (medication.getDeletedAt() != null) {
            throw new NotFoundException("Medication not found: " + medicationId);
        }
        return medication;
    }

    private PrescriptionItem findItemOrThrow(String prescriptionId, String itemId) {
        PrescriptionItem item = prescriptionItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Prescription item not found: " + itemId));
        if (item.getDeletedAt() != null || !item.getPrescription().getPrescriptionId().equals(prescriptionId)) {
            throw new NotFoundException("Prescription item not found: " + itemId);
        }
        return item;
    }

    private PrescriptionItemResponse toResponse(PrescriptionItem item) {
        PrescriptionItemResponse response = new PrescriptionItemResponse();
        response.setItemId(item.getItemId());
        response.setPrescriptionId(item.getPrescription().getPrescriptionId());
        response.setMedicationId(item.getMedication().getMedicationId());
        response.setMedicationName(item.getMedication().getName());
        response.setDosage(item.getDosage());
        response.setQuantity(item.getQuantity());
        response.setInstructions(item.getInstructions());
        return response;
    }
}
