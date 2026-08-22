package amalitech.hospital.management.repository.finance;

import amalitech.hospital.management.enums.PaymentStatus;
import amalitech.hospital.management.model.finance.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@code @EntityGraph(attributePaths = {"appointment", "patient"})} on every finder here
 * (HMS v5) — {@code InvoiceService.toResponse}/{@code PatientService} both walk
 * {@code invoice.getAppointment()}/{@code invoice.getPatient()} per row
 * ({@code Invoice}'s own {@code @ManyToOne(LAZY)} fields), which without this is one
 * extra `SELECT` per association per invoice — a real N+1 for any paginated invoice
 * listing or a patient with several invoices. Matches exactly what each caller actually
 * touches, not a blanket graph.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    // Backs PatientService.getPatient's eager-loaded invoices list.
    @EntityGraph(attributePaths = {"appointment", "patient"})
    List<Invoice> findByPatient_PatientIdAndDeletedAtIsNull(String patientId);

    // Backs InvoiceService.getInvoices' optional ?paymentStatus= filter — e.g. "show me
    // every unpaid invoice" for a billing follow-up worklist.
    @EntityGraph(attributePaths = {"appointment", "patient"})
    Page<Invoice> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    // Redeclares the inherited JpaRepository method purely to attach the same graph —
    // backs InvoiceService.getInvoices' unfiltered (no ?paymentStatus=) listing.
    @Override
    @EntityGraph(attributePaths = {"appointment", "patient"})
    Page<Invoice> findAll(Pageable pageable);

    // Same graph on the single-item lookup — InvoiceService.getInvoice's toResponse
    // walks the identical .getAppointment()/.getPatient() chain for that one row. Only
    // surfaced once spring.jpa.open-in-view was disabled (HMS v5) — previously masked
    // by OSIV keeping a session open for the whole request.
    @Override
    @EntityGraph(attributePaths = {"appointment", "patient"})
    Optional<Invoice> findById(String invoiceId);
}
