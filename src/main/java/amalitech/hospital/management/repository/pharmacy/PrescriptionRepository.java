package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, String> {
    /** Derived query, traversing prescription -> appointment -> patient (Prescription
     *  has no direct patient FK — see the entity's own Javadoc) — backs
     *  {@code PrescriptionService.getPrescriptions}' {@code patientId} filter. */
    Page<Prescription> findByAppointment_Patient_PatientIdAndDeletedAtIsNull(String patientId, Pageable pageable);
}
