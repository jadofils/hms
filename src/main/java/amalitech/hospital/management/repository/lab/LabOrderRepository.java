package amalitech.hospital.management.repository.lab;

import amalitech.hospital.management.enums.LabOrderStatus;
import amalitech.hospital.management.model.lab.LabOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code @EntityGraph(attributePaths = {"appointment", "doctor", "appointment.patient"})}
 * on every finder here (HMS v5) — {@code LabOrderService.toResponse} walks
 * {@code labOrder.getAppointment()}, {@code labOrder.getDoctor()}, and
 * {@code appointment.getPatient()} per row, all {@code @ManyToOne(LAZY)} — 3 extra
 * `SELECT`s per lab order without this.
 */
public interface LabOrderRepository extends JpaRepository<LabOrder, String> {

    // Backs LabOrderService.getLabOrders' optional ?status= filter — e.g. "show me
    // every still-pending order" for a lab technician's worklist.
    @EntityGraph(attributePaths = {"appointment", "doctor", "appointment.patient"})
    Page<LabOrder> findByStatus(LabOrderStatus status, Pageable pageable);

    // Redeclares the inherited JpaRepository method purely to attach the same graph —
    // backs LabOrderService.getLabOrders' unfiltered (no ?status=) listing.
    @Override
    @EntityGraph(attributePaths = {"appointment", "doctor", "appointment.patient"})
    Page<LabOrder> findAll(Pageable pageable);

    // Same graph on the single-item lookup — findLabOrderOrThrow (getLabOrder/update/
    // delete) resolves through this, and getLabOrder's toResponse walks the identical
    // lazy chain for that one row.
    @Override
    @EntityGraph(attributePaths = {"appointment", "doctor", "appointment.patient"})
    Optional<LabOrder> findById(String labOrderId);
}
