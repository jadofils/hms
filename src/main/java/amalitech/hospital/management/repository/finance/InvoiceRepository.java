package amalitech.hospital.management.repository.finance;

import amalitech.hospital.management.model.finance.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    // Backs PatientService.getPatient's eager-loaded invoices list.
    List<Invoice> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);
}
