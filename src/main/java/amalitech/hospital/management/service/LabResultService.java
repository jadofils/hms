package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.lab.LabResultRequest;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.event.LabResultRecordedEvent;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.lab.LabOrder;
import amalitech.hospital.management.model.lab.LabResult;
import amalitech.hospital.management.repository.lab.LabOrderRepository;
import amalitech.hospital.management.repository.lab.LabResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The single result for a {@link LabOrder} — a genuine one-to-one (the entity's own
 * {@code lab_order_id} column carries a hard, DB-level {@code unique} constraint, not
 * just an application-level check), so this is scoped under a {@code labOrderId} with
 * no list endpoint, same shape as {@code DoctorScheduleService} being scoped under a
 * {@code doctorId} — just singular instead of a collection.
 */
@Service
@RequiredArgsConstructor
public class LabResultService {

    private final LabResultRepository labResultRepository;
    private final LabOrderRepository labOrderRepository;
    private final EventBus eventBus;

    public LabResultResponse getResult(String labOrderId) {
        return toResponse(findResultOrThrow(labOrderId));
    }

    @Transactional
    public LabResultResponse createResult(String labOrderId, LabResultRequest request) {
        LabOrder labOrder = findLabOrderOrThrow(labOrderId);
        labResultRepository.findByLabOrder_LabOrderId(labOrderId)
                .filter(existing -> existing.getDeletedAt() == null)
                .ifPresent(existing -> {
                    throw new ConflictException("Lab order already has a result");
                });

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LabResult result = new LabResult();
        result.setLabOrder(labOrder);
        result.setResultValue(request.getResultValue());
        result.setUnit(request.getUnit());
        result.setReferenceRange(request.getReferenceRange());
        result.setIsAbnormal(request.getIsAbnormal() != null && request.getIsAbnormal());
        result.setCompletedAt(request.getCompletedAt());
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        LabResult saved = labResultRepository.save(result);
        eventBus.publish(new LabResultRecordedEvent(saved));
        return toResponse(saved);
    }

    @Transactional
    public LabResultResponse updateResult(String labOrderId, LabResultRequest request) {
        LabResult result = findResultOrThrow(labOrderId);
        result.setResultValue(request.getResultValue());
        result.setUnit(request.getUnit());
        result.setReferenceRange(request.getReferenceRange());
        result.setIsAbnormal(request.getIsAbnormal() != null && request.getIsAbnormal());
        result.setCompletedAt(request.getCompletedAt());
        result.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(labResultRepository.save(result));
    }

    /**
     * Partial-update counterpart to {@link #updateResult} — reuses {@link LabResultRequest}
     * itself rather than a dedicated Patch DTO (every field there is already optional/
     * nullable at the validation level, unlike every other entity's Request DTO), but
     * still needs its own method: {@code updateResult}'s body unconditionally overwrites
     * every field with whatever the request carries (including {@code null}), so it's
     * actually full-replace semantics under the hood despite the DTO looking
     * patch-friendly — reusing it directly would silently null out any field the caller
     * left out, which is exactly what a real PATCH must not do.
     */
    @Transactional
    public LabResultResponse patchResult(String labOrderId, LabResultRequest patch) {
        LabResult result = findResultOrThrow(labOrderId);
        if (patch.getResultValue() != null) {
            result.setResultValue(patch.getResultValue());
        }
        if (patch.getUnit() != null) {
            result.setUnit(patch.getUnit());
        }
        if (patch.getReferenceRange() != null) {
            result.setReferenceRange(patch.getReferenceRange());
        }
        if (patch.getIsAbnormal() != null) {
            result.setIsAbnormal(patch.getIsAbnormal());
        }
        if (patch.getCompletedAt() != null) {
            result.setCompletedAt(patch.getCompletedAt());
        }
        result.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(labResultRepository.save(result));
    }

    @Transactional
    public void deleteResult(String labOrderId) {
        LabResult result = findResultOrThrow(labOrderId);
        result.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        labResultRepository.save(result);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LabOrder findLabOrderOrThrow(String labOrderId) {
        LabOrder labOrder = labOrderRepository.findById(labOrderId)
                .orElseThrow(() -> new NotFoundException("Lab order not found: " + labOrderId));
        if (labOrder.getDeletedAt() != null) {
            throw new NotFoundException("Lab order not found: " + labOrderId);
        }
        return labOrder;
    }

    private LabResult findResultOrThrow(String labOrderId) {
        findLabOrderOrThrow(labOrderId);
        return labResultRepository.findByLabOrder_LabOrderId(labOrderId)
                .filter(result -> result.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Result not found for lab order: " + labOrderId));
    }

    private LabResultResponse toResponse(LabResult result) {
        LabResultResponse response = new LabResultResponse();
        response.setLabResultId(result.getLabResultId());
        response.setLabOrderId(result.getLabOrder().getLabOrderId());
        response.setResultValue(result.getResultValue());
        response.setUnit(result.getUnit());
        response.setReferenceRange(result.getReferenceRange());
        response.setIsAbnormal(result.getIsAbnormal());
        response.setCompletedAt(result.getCompletedAt());
        return response;
    }
}
