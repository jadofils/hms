package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
}
