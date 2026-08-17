package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientNoteRepository extends JpaRepository<PatientNote, String> {
    List<PatientNote> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
