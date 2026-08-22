package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Referral;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, String> {
    // Same nested-traversal reasoning as MedicalRecordRepository — Referral is keyed
    // by appointment_id, not patient_id. @EntityGraph (HMS v5) — PatientService.
    // toReferralResponse calls .getAppointment(), .getReferringDoctor(), and
    // .getReferredToDoctor() per row: 3 extra SELECTs per referral without this, the
    // worst single-row fan-out of any N+1 site in the codebase.
    @EntityGraph(attributePaths = {"appointment", "referringDoctor", "referredToDoctor"})
    List<Referral> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId);
}
