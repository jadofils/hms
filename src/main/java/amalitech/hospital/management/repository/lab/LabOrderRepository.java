package amalitech.hospital.management.repository.lab;

import amalitech.hospital.management.enums.LabOrderStatus;
import amalitech.hospital.management.model.lab.LabOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderRepository extends JpaRepository<LabOrder, String> {
    // Backs LabOrderService.getLabOrders' optional ?status= filter — e.g. "show me
    // every still-pending order" for a lab technician's worklist.
    Page<LabOrder> findByStatus(LabOrderStatus status, Pageable pageable);
}
