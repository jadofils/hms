package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.PatientFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientFeedbackRepository extends JpaRepository<PatientFeedback, String> {
    List<PatientFeedback> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
