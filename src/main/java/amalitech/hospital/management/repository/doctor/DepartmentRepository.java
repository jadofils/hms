package amalitech.hospital.management.repository.doctor;

import amalitech.hospital.management.model.doctor.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    boolean existsByName(String name);
    boolean existsByPhone(String phone);

    // @EntityGraph (HMS v5) — DepartmentService.getDepartment's response walks the
    // @ManyToMany(LAZY) doctors collection; only surfaced once spring.jpa.open-in-view
    // was disabled (previously masked by OSIV).
    @Override
    @EntityGraph(attributePaths = "doctors")
    Optional<Department> findById(String departmentId);
}
