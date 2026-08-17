package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, String> {
    // Same nested-traversal reasoning as MedicalRecordRepository — Referral is keyed
    // by appointment_id, not patient_id.
    List<Referral> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId);
}
