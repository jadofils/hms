package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.lab.LabOrderRequest;
import amalitech.hospital.management.dto.lab.LabOrderResponse;
import amalitech.hospital.management.enums.LabOrderStatus;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.lab.LabOrder;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.lab.LabOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Lab order CRUD — each order belongs to one {@link Appointment} and is requested by
 * one ordering {@link Doctor}. Results are managed separately — see
 * {@code LabResultService}.
 *
 * Single-item lookups are cached in Redis under the "lab-orders" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public PagedModel<LabOrderResponse> getLabOrders(Pageable pageable) {
        return new PagedModel<>(labOrderRepository.findAll(pageable).map(this::toResponse));
    }

    @Cacheable(value = "lab-orders", key = "#labOrderId")
    public LabOrderResponse getLabOrder(String labOrderId) {
        return toResponse(findLabOrderOrThrow(labOrderId));
    }

    @Transactional
    public LabOrderResponse createLabOrder(LabOrderRequest request) {
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());

        LocalDateTime now = LocalDateTime.now();
        LabOrder labOrder = new LabOrder();
        labOrder.setAppointment(appointment);
        labOrder.setDoctor(doctor);
        labOrder.setTestName(request.getTestName());
        labOrder.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? LabOrderStatus.ORDERED : validateStatus(request.getStatus()));
        labOrder.setOrderedAt(now);
        labOrder.setUpdatedAt(now);
        return toResponse(labOrderRepository.save(labOrder));
    }

    @Transactional
    @CachePut(value = "lab-orders", key = "#labOrderId")
    public LabOrderResponse updateLabOrder(String labOrderId, LabOrderRequest request) {
        LabOrder labOrder = findLabOrderOrThrow(labOrderId);
        Appointment appointment = findAppointmentOrThrow(request.getAppointmentId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());

        labOrder.setAppointment(appointment);
        labOrder.setDoctor(doctor);
        labOrder.setTestName(request.getTestName());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            labOrder.setStatus(validateStatus(request.getStatus()));
        }
        labOrder.setUpdatedAt(LocalDateTime.now());
        return toResponse(labOrderRepository.save(labOrder));
    }

    @Transactional
    @CacheEvict(value = "lab-orders", key = "#labOrderId")
    public void deleteLabOrder(String labOrderId) {
        LabOrder labOrder = findLabOrderOrThrow(labOrderId);
        labOrder.setDeletedAt(LocalDateTime.now());
        labOrderRepository.save(labOrder);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LabOrder findLabOrderOrThrow(String labOrderId) {
        LabOrder labOrder = labOrderRepository.findById(labOrderId)
                .orElseThrow(() -> new NotFoundException("Lab order not found: " + labOrderId));
        if (labOrder.getDeletedAt() != null) {
            throw new NotFoundException("Lab order not found: " + labOrderId);
        }
        return labOrder;
    }

    private Appointment findAppointmentOrThrow(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + appointmentId));
        if (appointment.getDeletedAt() != null) {
            throw new NotFoundException("Appointment not found: " + appointmentId);
        }
        return appointment;
    }

    private Doctor findDoctorOrThrow(String doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
        if (doctor.getDeletedAt() != null) {
            throw new NotFoundException("Doctor not found: " + doctorId);
        }
        return doctor;
    }

    /** The DTO's own {@code @Pattern} already constrains this to an allowed value, so
     *  {@link LabOrderStatus#fromDbValue} should never actually throw here — this is
     *  defense in depth, not the primary validation path. */
    private LabOrderStatus validateStatus(String status) {
        try {
            return LabOrderStatus.fromDbValue(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private LabOrderResponse toResponse(LabOrder labOrder) {
        Appointment appointment = labOrder.getAppointment();
        Doctor doctor = labOrder.getDoctor();
        LabOrderResponse response = new LabOrderResponse();
        response.setLabOrderId(labOrder.getLabOrderId());
        response.setAppointmentId(appointment.getAppointmentId());
        response.setPatientId(appointment.getPatient().getPatientId());
        response.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        response.setDoctorId(doctor.getDoctorId());
        response.setDoctorName(doctor.getFirstName() + " " + doctor.getLastName());
        response.setTestName(labOrder.getTestName());
        response.setStatus(labOrder.getStatus().getDbValue());
        response.setOrderedAt(labOrder.getOrderedAt());
        return response;
    }
}
