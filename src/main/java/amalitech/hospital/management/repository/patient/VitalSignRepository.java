package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, String> {
    // Same nested-traversal reasoning as MedicalRecordRepository — VitalSign is keyed
    // by appointment_id, not patient_id.
    List<VitalSign> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId);
}
