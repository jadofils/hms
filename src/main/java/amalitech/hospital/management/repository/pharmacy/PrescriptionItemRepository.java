package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, String> {
    List<PrescriptionItem> findByPrescription_PrescriptionIdAndDeletedAtIsNull(String prescriptionId);
}
