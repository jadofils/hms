package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code @EntityGraph(attributePaths = {"appointment", "appointment.patient",
 * "appointment.doctor"})} on every finder here (HMS v5) — {@code PrescriptionService}'s
 * {@code toResponse} walks {@code prescription.getAppointment()} then, off that,
 * {@code appointment.getPatient()}/{@code appointment.getDoctor()} — a 2-hop lazy chain
 * ({@code Prescription} → {@code Appointment} → {@code Patient}/{@code Doctor}, all
 * {@code @ManyToOne(LAZY)}) fired per row without this, the worst N+1 of the five
 * originally found: 3 extra `SELECT`s per prescription.
 */
public interface PrescriptionRepository extends JpaRepository<Prescription, String> {

    /** Derived query, traversing prescription -> appointment -> patient (Prescription
     *  has no direct patient FK — see the entity's own Javadoc) — backs
     *  {@code PrescriptionService.getPrescriptions}' {@code patientId} filter. */
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Page<Prescription> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId, Pageable pageable);

    // Redeclares the inherited JpaRepository method purely to attach the same graph —
    // backs PrescriptionService.getPrescriptions' unfiltered (no ?patientId=) listing.
    @Override
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Page<Prescription> findAll(Pageable pageable);

    // Same graph on the single-item lookup — PrescriptionService.getPrescription/
    // updatePrescription/deletePrescription all resolve through findPrescriptionOrThrow,
    // and getPrescription's toResponse walks the identical lazy chain for that one row.
    @Override
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<Prescription> findById(String prescriptionId);
}
