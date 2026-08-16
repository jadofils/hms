package amalitech.hospital.management.repository.doctor;

import amalitech.hospital.management.model.doctor.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, String> {
    List<DoctorSchedule> findByDoctorIdAndDeletedAtIsNull(String doctorId);
}
