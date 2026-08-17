package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    // Backs PatientService.getPatient's eager-loaded appointments list.
    List<Appointment> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
