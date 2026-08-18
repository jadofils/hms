package amalitech.hospital.management.repository.finance;

import amalitech.hospital.management.enums.PaymentStatus;
import amalitech.hospital.management.model.finance.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    // Backs PatientService.getPatient's eager-loaded invoices list.
    List<Invoice> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);

    // Backs InvoiceService.getInvoices' optional ?paymentStatus= filter — e.g. "show me
    // every unpaid invoice" for a billing follow-up worklist.
    Page<Invoice> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);
}
