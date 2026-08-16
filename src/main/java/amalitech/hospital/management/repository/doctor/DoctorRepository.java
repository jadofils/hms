package amalitech.hospital.management.repository.doctor;

import amalitech.hospital.management.model.doctor.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
