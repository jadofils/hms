package amalitech.hospital.management.repository.doctor;

import amalitech.hospital.management.model.doctor.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    boolean existsByName(String name);
    boolean existsByPhone(String phone);
}
