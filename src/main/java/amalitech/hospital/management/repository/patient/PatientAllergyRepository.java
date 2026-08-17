package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, String> {
    List<PatientAllergy> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
