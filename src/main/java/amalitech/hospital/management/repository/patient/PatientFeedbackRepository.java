package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.PatientFeedback;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientFeedbackRepository extends JpaRepository<PatientFeedback, String> {
    // @EntityGraph (HMS v5) — PatientService.toFeedbackResponse calls .getAppointment()
    // per row (@ManyToOne(LAZY)): 1 extra SELECT per feedback row without this.
    @EntityGraph(attributePaths = "appointment")
    List<PatientFeedback> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
