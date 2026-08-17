package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, String> {
    // MedicalRecord has no direct patient_id FK — it's keyed by appointment_id, so
    // reaching "all records for a patient" traverses appointment.patient.patientId
    // (Spring Data supports this nested-property derivation without a custom @Query).
    List<MedicalRecord> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId);
}
