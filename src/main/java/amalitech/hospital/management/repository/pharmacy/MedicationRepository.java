package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, String> {
    boolean existsByName(String name);
}
