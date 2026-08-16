package amalitech.hospital.management.repository.lab;

import amalitech.hospital.management.model.lab.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabResultRepository extends JpaRepository<LabResult, String> {
    Optional<LabResult> findByLabOrder_LabOrderId(String labOrderId);
}
