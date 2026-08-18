package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    // Backs PatientService.getPatient's eager-loaded appointments list.
    List<Appointment> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);

    // Backs AppointmentService's double-booking check — loads one doctor's own active
    // appointments so a requested slot can be located in memory (sort + binary search)
    // instead of a second, date-filtered query per create/update call.
    List<Appointment> findByDoctor_DoctorIdAndDeletedAtIsNull(String doctorId);
}
