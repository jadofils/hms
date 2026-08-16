package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
