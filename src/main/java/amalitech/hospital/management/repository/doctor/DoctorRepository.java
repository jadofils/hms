package amalitech.hospital.management.repository.doctor;

import amalitech.hospital.management.model.doctor.Doctor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // @EntityGraph (HMS v5) — DoctorService.getDoctor's response walks the
    // @ManyToMany(LAZY) departments collection; only surfaced once
    // spring.jpa.open-in-view was disabled (previously masked by OSIV).
    @Override
    @EntityGraph(attributePaths = "departments")
    Optional<Doctor> findById(String doctorId);
}
