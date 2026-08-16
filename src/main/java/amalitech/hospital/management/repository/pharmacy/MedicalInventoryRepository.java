package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.MedicalInventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalInventoryRepository extends JpaRepository<MedicalInventory, String> {
}
