package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.PatientNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientNoteRepository extends JpaRepository<PatientNote, String> {
    // @EntityGraph (HMS v5) — PatientService.toNoteResponse calls .getAppointment() and
    // .getAuthor() per row (both @ManyToOne(LAZY)): 2 extra SELECTs per note without this.
    @EntityGraph(attributePaths = {"appointment", "author"})
    List<PatientNote> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
