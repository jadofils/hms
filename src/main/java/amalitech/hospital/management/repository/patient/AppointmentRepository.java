package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    // Backs PatientService.getPatient's eager-loaded appointments list — @EntityGraph
    // (HMS v5) since PatientService.toAppointmentResponse walks .getPatient()/.getDoctor()
    // per row, both @ManyToOne(LAZY): 2 extra SELECTs per appointment without this.
    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);

    // Backs AppointmentService's double-booking check — loads one doctor's own active
    // appointments so a requested slot can be located in memory (sort + binary search)
    // instead of a second, date-filtered query per create/update call.
    List<Appointment> findByDoctor_DoctorIdAndDeletedAtIsNull(String doctorId);

    // Same graph on the single-item lookup — AppointmentService.getAppointment's
    // toResponse walks the identical .getPatient()/.getDoctor() chain for that one row.
    // Only surfaced once spring.jpa.open-in-view was disabled (HMS v5) — previously
    // masked by OSIV keeping a session open for the whole request.
    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findById(String appointmentId);
}
