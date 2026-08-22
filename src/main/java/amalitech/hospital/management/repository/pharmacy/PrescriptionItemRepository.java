package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.PrescriptionItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@code @EntityGraph(attributePaths = {"prescription", "medication"})} (HMS v5) — both
 * {@code PrescriptionItemService.toResponse} and {@code PrescriptionService.toItemResponse}
 * (the same lazy-walk duplicated in two places) call {@code item.getPrescription()}/
 * {@code item.getMedication()} per row — 2 extra `SELECT`s per item without this.
 */
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, String> {

    @EntityGraph(attributePaths = {"prescription", "medication"})
    List<PrescriptionItem> findByPrescription_PrescriptionIdAndDeletedAtIsNull(String prescriptionId);
}
