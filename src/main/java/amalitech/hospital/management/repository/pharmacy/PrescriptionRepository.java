package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, String> {
}
