package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.VitalSign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, String> {
    // Same nested-traversal reasoning as MedicalRecordRepository — VitalSign is keyed
    // by appointment_id, not patient_id. @EntityGraph (HMS v5) — PatientService.
    // toVitalSignResponse calls .getAppointment() per row: 1 extra SELECT without this.
    @EntityGraph(attributePaths = "appointment")
    List<VitalSign> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId);
}
