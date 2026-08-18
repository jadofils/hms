package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionResponse;
import amalitech.hospital.management.event.PrescriptionCreatedEvent;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.pharmacy.Prescription;
import amalitech.hospital.management.model.pharmacy.PrescriptionItem;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionItemRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Prescription CRUD — each prescription belongs to one {@link Appointment}.
 *
 * Single-item lookups are cached in Redis under the "prescriptions" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final EventBus eventBus;

    public PagedModel<PrescriptionResponse> getPrescriptions(Pageable pageable) {
        return new PagedModel<>(prescriptionRepository.findAll(pageable).map(this::toResponse));
    }

    /** Not populated by {@link #getPrescriptions} or by create/update — only by this
     *  single-item lookup, same convention as {@code DoctorService.getDoctor}. */
    @Cacheable(value = "prescriptions", key = "#prescriptionId")
    public PrescriptionResponse getPrescription(String prescriptionId) {
        PrescriptionResponse response = toResponse(findPrescriptionOrThrow(prescriptionId));
        response.setItems(prescriptionItemRepository
                .findByPrescription_PrescriptionIdAndDeletedAtIsNull(prescriptionId).stream()
                .map(this::toItemResponse).toList());
        return response;
    }

    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest request) {
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setDateIssued(request.getDateIssued() == null ? LocalDate.now(ZoneId.systemDefault()) : request.getDateIssued());
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);
        Prescription saved = prescriptionRepository.save(prescription);
        eventBus.publish(new PrescriptionCreatedEvent(saved));
        return toResponse(saved);
    }

    @Transactional
    @CachePut(value = "prescriptions", key = "#prescriptionId")
    public PrescriptionResponse updatePrescription(String prescriptionId, PrescriptionRequest request) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());

        prescription.setAppointment(appointment);
        prescription.setDateIssued(request.getDateIssued() == null ? LocalDate.now(ZoneId.systemDefault()) : request.getDateIssued());
        prescription.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(prescriptionRepository.save(prescription));
    }

    @Transactional
    @CacheEvict(value = "prescriptions", key = "#prescriptionId")
    public void deletePrescription(String prescriptionId) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        prescription.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        prescriptionRepository.save(prescription);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Prescription findPrescriptionOrThrow(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Prescription not found: " + prescriptionId));
        if (prescription.getDeletedAt() != null) {
            throw new NotFoundException("Prescription not found: " + prescriptionId);
        }
        return prescription;
    }

    private Appointment findAppointmentOrThrow(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + appointmentId));
        if (appointment.getDeletedAt() != null) {
            throw new NotFoundException("Appointment not found: " + appointmentId);
        }
        return appointment;
    }

    private PrescriptionResponse toResponse(Prescription prescription) {
        Appointment appointment = prescription.getAppointment();
        PrescriptionResponse response = new PrescriptionResponse();
        response.setPrescriptionId(prescription.getPrescriptionId());
        response.setAppointmentId(appointment.getAppointmentId());
        response.setPatientId(appointment.getPatient().getPatientId());
        response.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        response.setDoctorId(appointment.getDoctor().getDoctorId());
        response.setDoctorName(appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName());
        response.setDateIssued(prescription.getDateIssued());
        return response;
    }

    /** Mirrors {@code PrescriptionItemService}'s own mapping exactly (same flattened
     *  shape), used only by {@link #getPrescription}'s eager-loaded {@code items}. */
    private PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
        PrescriptionItemResponse response = new PrescriptionItemResponse();
        response.setItemId(item.getItemId());
        response.setPrescriptionId(item.getPrescription().getPrescriptionId());
        response.setMedicationId(item.getMedication().getMedicationId());
        response.setMedicationName(item.getMedication().getName());
        response.setDosage(item.getDosage());
        response.setQuantity(item.getQuantity());
        response.setInstructions(item.getInstructions());
        return response;
    }
}
