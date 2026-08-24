package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.finance.InvoiceRequest;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.dto.finance.PatchInvoiceRequest;
import amalitech.hospital.management.enums.PaymentStatus;
import amalitech.hospital.management.event.InvoiceCreatedEvent;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.finance.Invoice;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.repository.finance.InvoiceRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.patient.PatientRepository;
import amalitech.hospital.management.utils.PageableDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Invoice CRUD — each invoice belongs to one {@link Appointment} and one {@link Patient}.
 *
 * Single-item lookups are cached in Redis under the "invoices" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final EventBus eventBus;

    /**
     * {@code paymentStatus} is optional — omitted, this is every invoice (unfiltered);
     * given, only invoices in that exact status (e.g. {@code "unpaid"} for a billing
     * follow-up worklist). Validated against {@link PaymentStatus}'s own allowed values
     * before ever reaching a query — the same safety principle
     * {@code PatientService.getPatients}/{@code AppointmentService.getAppointments}
     * already rely on for their own status filters.
     */
    public PagedModel<InvoiceResponse> getInvoices(Pageable pageable, String paymentStatus) {
        // Defaults to issuedAt DESC (matching this endpoint's own Swagger sort example)
        // when the caller sends no ?sort= at all — see PageableDefaults' own Javadoc.
        Pageable sorted = PageableDefaults.withDefaultSort(pageable, "issuedAt", Sort.Direction.DESC);
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return new PagedModel<>(invoiceRepository.findAll(sorted).map(this::toResponse));
        }
        PaymentStatus validated = validateStatus(paymentStatus);
        return new PagedModel<>(invoiceRepository.findByPaymentStatus(validated, sorted).map(this::toResponse));
    }

    @Cacheable(value = "invoices", key = "#invoiceId")
    public InvoiceResponse getInvoice(String invoiceId) {
        return toResponse(findInvoiceOrThrow(invoiceId));
    }

    @Transactional
    public InvoiceResponse createInvoice(InvoiceRequest request) {
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());
        Patient patient = findPatientOrThrow(request.getPatientId());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Invoice invoice = new Invoice();
        invoice.setAppointment(appointment);
        invoice.setPatient(patient);
        invoice.setTotalAmount(request.getTotalAmount() == null ? BigDecimal.ZERO : request.getTotalAmount());
        invoice.setPaymentStatus(request.getPaymentStatus() == null || request.getPaymentStatus().isBlank()
                ? PaymentStatus.UNPAID : validateStatus(request.getPaymentStatus()));
        invoice.setIssuedAt(now);
        invoice.setUpdatedAt(now);
        Invoice saved = invoiceRepository.save(invoice);
        eventBus.publish(new InvoiceCreatedEvent(saved));
        return toResponse(saved);
    }

    @Transactional
    @CachePut(value = "invoices", key = "#invoiceId")
    public InvoiceResponse updateInvoice(String invoiceId, InvoiceRequest request) {
        Invoice invoice = findInvoiceOrThrow(invoiceId);
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());
        Patient patient = findPatientOrThrow(request.getPatientId());

        invoice.setAppointment(appointment);
        invoice.setPatient(patient);
        invoice.setTotalAmount(request.getTotalAmount() == null ? BigDecimal.ZERO : request.getTotalAmount());
        if (request.getPaymentStatus() != null && !request.getPaymentStatus().isBlank()) {
            invoice.setPaymentStatus(validateStatus(request.getPaymentStatus()));
        }
        invoice.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(invoiceRepository.save(invoice));
    }

    /**
     * Partial-update counterpart to {@link #updateInvoice} — only the fields actually
     * present in {@code patch} are changed; everything else on the existing invoice is
     * left untouched (unlike {@code updateInvoice}, an omitted {@code totalAmount}
     * here is left as-is rather than reset to zero).
     */
    @Transactional
    @CachePut(value = "invoices", key = "#invoiceId")
    public InvoiceResponse patchInvoice(String invoiceId, PatchInvoiceRequest patch) {
        Invoice invoice = findInvoiceOrThrow(invoiceId);
        if (patch.getAppointmentId() != null) {
            invoice.setAppointment(findAppointmentOrThrow(patch.getAppointmentId()));
        }
        if (patch.getPatientId() != null) {
            invoice.setPatient(findPatientOrThrow(patch.getPatientId()));
        }
        if (patch.getTotalAmount() != null) {
            invoice.setTotalAmount(patch.getTotalAmount());
        }
        if (patch.getPaymentStatus() != null && !patch.getPaymentStatus().isBlank()) {
            invoice.setPaymentStatus(validateStatus(patch.getPaymentStatus()));
        }
        invoice.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public void deleteInvoice(String invoiceId) {
        Invoice invoice = findInvoiceOrThrow(invoiceId);
        invoice.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        invoiceRepository.save(invoice);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Invoice findInvoiceOrThrow(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));
        if (invoice.getDeletedAt() != null) {
            throw new NotFoundException("Invoice not found: " + invoiceId);
        }
        return invoice;
    }

    private Appointment findAppointmentOrThrow(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + appointmentId));
        if (appointment.getDeletedAt() != null) {
            throw new NotFoundException("Appointment not found: " + appointmentId);
        }
        return appointment;
    }

    private Patient findPatientOrThrow(String patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + patientId));
        if (patient.getDeletedAt() != null) {
            throw new NotFoundException("Patient not found: " + patientId);
        }
        return patient;
    }

    /** The DTO's own {@code @Pattern} already constrains this to an allowed value, so
     *  {@link PaymentStatus#fromDbValue} should never actually throw here — this is
     *  defense in depth, not the primary validation path. */
    private PaymentStatus validateStatus(String status) {
        try {
            return PaymentStatus.fromDbValue(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setInvoiceId(invoice.getInvoiceId());
        response.setAppointmentId(invoice.getAppointment().getAppointmentId());
        response.setPatientId(invoice.getPatient().getPatientId());
        response.setPatientName(invoice.getPatient().getFirstName() + " " + invoice.getPatient().getLastName());
        response.setTotalAmount(invoice.getTotalAmount());
        response.setPaymentStatus(invoice.getPaymentStatus().getDbValue());
        response.setIssuedAt(invoice.getIssuedAt());
        return response;
    }
}
