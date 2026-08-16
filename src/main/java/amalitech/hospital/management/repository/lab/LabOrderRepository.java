package amalitech.hospital.management.repository.lab;

import amalitech.hospital.management.model.lab.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderRepository extends JpaRepository<LabOrder, String> {
}
