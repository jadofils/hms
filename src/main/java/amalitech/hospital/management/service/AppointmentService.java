package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.patient.AppointmentRequest;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.enums.AppointmentStatus;
import amalitech.hospital.management.event.AppointmentCreatedEvent;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.patient.PatientRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Appointment CRUD.
 *
 * Single-item lookups are cached in Redis under the "appointments" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final EventBus eventBus;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findAppointmentsPage}. {@code @Lazy} breaks the circular dependency this
     * creates at bean-creation time.
     */
    @Lazy
    private final AppointmentService self;

    /**
     * Listing is served through {@link #findAppointmentsPage}, an
     * {@code @FindUserData}-annotated method (AOP-driven native SQL — see
     * {@link amalitech.hospital.management.aop.FindUserDataAspect}), the same pattern
     * {@code PatientService.getPatients} uses. An optional {@code status} filter is
     * validated against {@link AppointmentStatus}'s own allowed values first — only an
     * already-validated enum {@code dbValue} is ever concatenated into the query.
     */
    public PagedModel<AppointmentResponse> getAppointments(Pageable pageable, String status) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;
        String statusFilter = status == null || status.isBlank() ? null : validateStatus(status).getDbValue();

        PagedRawResult raw = self.findAppointmentsPage(
                pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir, statusFilter);
        List<AppointmentResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    AppointmentResponse response = new AppointmentResponse();
                    response.setAppointmentId((String) cols[0]);
                    response.setPatientId((String) cols[1]);
                    response.setDoctorId((String) cols[2]);
                    response.setPatientName(cols[3] + " " + cols[4]);
                    response.setDoctorName(cols[5] + " " + cols[6]);
                    response.setAppointmentDate(toLocalDateTime(cols[7]));
                    response.setStatus((String) cols[8]);
                    response.setReason((String) cols[9]);
                    return response;
                })
                .toList();
        Page<AppointmentResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findAppointmentsPage(...)}: Spring AOP proxies only intercept
     * calls made through the proxy, so a same-class call would bypass the aspect and
     * fall through to the body below.
     */
    @FindUserData(domain = "appointment")
    public PagedRawResult findAppointmentsPage(int page, int size, String sortBy, String sortDir, String status) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    /** Not populated by {@link #getAppointments} or by create/update — only by this
     *  single-item lookup, same convention as {@code DoctorService.getDoctor}. Nests the
     *  full {@code patient}/{@code doctor} objects (one level deep — neither is itself
     *  further eager-loaded, to avoid dragging in e.g. every other appointment that same
     *  patient has) alongside the existing flattened {@code patientName}/{@code doctorName}
     *  scalars, which stay exactly as before. */
    @Cacheable(value = "appointments", key = "#appointmentId")
    public AppointmentResponse getAppointment(String appointmentId) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        AppointmentResponse response = toResponse(appointment);
        response.setPatient(toPatientResponse(appointment.getPatient()));
        response.setDoctor(toDoctorResponse(appointment.getDoctor()));
        return response;
    }

    @Transactional
    public AppointmentResponse createAppointment(AppointmentRequest request) {
        Patient patient = findPatientOrThrow(request.getPatientId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());

        LocalDateTime now = LocalDateTime.now();
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setReason(request.getReason());
        appointment.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? AppointmentStatus.SCHEDULED : validateStatus(request.getStatus()));
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
        Appointment saved = appointmentRepository.save(appointment);
        eventBus.publish(new AppointmentCreatedEvent(saved));
        return toResponse(saved);
    }

    @Transactional
    @CachePut(value = "appointments", key = "#appointmentId")
    public AppointmentResponse updateAppointment(String appointmentId, AppointmentRequest request) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        Patient patient = findPatientOrThrow(request.getPatientId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setReason(request.getReason());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            appointment.setStatus(validateStatus(request.getStatus()));
        }
        appointment.setUpdatedAt(LocalDateTime.now());
        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    @CacheEvict(value = "appointments", key = "#appointmentId")
    public void deleteAppointment(String appointmentId) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        appointment.setDeletedAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private Doctor findDoctorOrThrow(String doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
        if (doctor.getDeletedAt() != null) {
            throw new NotFoundException("Doctor not found: " + doctorId);
        }
        return doctor;
    }

    /** The DTO's own {@code @Pattern} already constrains this to an allowed value, so
     *  {@link AppointmentStatus#fromDbValue} should never actually throw here — this is
     *  defense in depth, not the primary validation path. */
    private AppointmentStatus validateStatus(String status) {
        try {
            return AppointmentStatus.fromDbValue(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** A native query with no result-class mapping returns {@link Timestamp} for a
     *  timestamp column on some drivers and {@link LocalDateTime} on others — accept
     *  either rather than assuming one. */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime ldt) return ldt;
        return null;
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        AppointmentResponse response = new AppointmentResponse();
        response.setAppointmentId(appointment.getAppointmentId());
        response.setPatientId(appointment.getPatient().getPatientId());
        response.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        response.setDoctorId(appointment.getDoctor().getDoctorId());
        response.setDoctorName(appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName());
        response.setAppointmentDate(appointment.getAppointmentDate());
        response.setStatus(appointment.getStatus().getDbValue());
        response.setReason(appointment.getReason());
        return response;
    }

    // ── Eager-loaded related data (getAppointment only) ─────────────────────────
    // Deliberately one level deep only — neither nested object populates its own
    // departments/roles/etc., so looking up one appointment never drags in a patient's
    // or doctor's entire other history.

    private PatientResponse toPatientResponse(Patient patient) {
        PatientResponse response = new PatientResponse();
        response.setPatientId(patient.getPatientId());
        response.setFirstName(patient.getFirstName());
        response.setLastName(patient.getLastName());
        response.setDob(patient.getDob());
        response.setGender(patient.getGender().getDbValue());
        response.setPhone(patient.getPhone());
        response.setEmail(patient.getEmail());
        response.setAddress(patient.getAddress());
        response.setStatus(patient.getStatus().getDbValue());
        return response;
    }

    private DoctorResponse toDoctorResponse(Doctor doctor) {
        DoctorResponse response = new DoctorResponse();
        response.setDoctorId(doctor.getDoctorId());
        response.setFirstName(doctor.getFirstName());
        response.setLastName(doctor.getLastName());
        response.setSpecialization(doctor.getSpecialization());
        response.setPhone(doctor.getPhone());
        response.setEmail(doctor.getEmail());
        return response;
    }
}
