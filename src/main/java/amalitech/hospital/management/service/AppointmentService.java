package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.ApplyAlgorithm;
import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.patient.AppointmentRequest;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.enums.AppointmentStatus;
import amalitech.hospital.management.event.AppointmentCreatedEvent;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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

    /**
     * {@code Isolation.REPEATABLE_READ} (Postgres' own {@code REPEATABLE READ} is
     * snapshot isolation, not the row-locking kind the SQL standard's name suggests) —
     * the default {@code READ_COMMITTED} lets a second concurrent transaction's commit
     * become visible mid-transaction, which matters here specifically because
     * {@link #throwIfDoctorDoubleBooked} reads the doctor's appointments once and this
     * method writes a new one afterward in the same transaction; a stable snapshot for
     * the whole transaction is the correct level for a "check, then act on what I just
     * checked" pattern. It's still not a complete fix on its own: two transactions
     * started at nearly the same instant can each take their own snapshot before either
     * commits, each see "no conflict," and both insert — full prevention needs either a
     * DB-level unique constraint on {@code (doctor_id, appointment_date)} or
     * {@code SERIALIZABLE} plus a commit-retry loop, neither of which this pass adds.
     * Documented here as the honest boundary of what this isolation level buys, not
     * oversold as closing the race outright.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AppointmentResponse createAppointment(AppointmentRequest request) {
        Patient patient = findPatientOrThrow(request.getPatientId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());
        throwIfDoctorDoubleBooked(doctor.getDoctorId(), request.getAppointmentDate(), null);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
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

    /** Same isolation-level reasoning as {@link #createAppointment} above. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CachePut(value = "appointments", key = "#appointmentId")
    public AppointmentResponse updateAppointment(String appointmentId, AppointmentRequest request) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        Patient patient = findPatientOrThrow(request.getPatientId());
        Doctor doctor = findDoctorOrThrow(request.getDoctorId());
        throwIfDoctorDoubleBooked(doctor.getDoctorId(), request.getAppointmentDate(), appointmentId);

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setReason(request.getReason());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            appointment.setStatus(validateStatus(request.getStatus()));
        }
        appointment.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    @CacheEvict(value = "appointments", key = "#appointmentId")
    public void deleteAppointment(String appointmentId) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        appointment.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        appointmentRepository.save(appointment);
    }

    // ── Double-booking guard ─────────────────────────────────────────────────

    /**
     * Rejects a request that would double-book the doctor at the exact same date/time.
     * Loads the doctor's own active appointments, sorts them by date via {@link #sort}
     * (an {@code @ApplyAlgorithm("mergeSort")} entry point), then locates the requested
     * slot via {@link #search} (an {@code @ApplyAlgorithm("binarySearch")} entry point)
     * instead of a second, date-filtered DB round trip. {@code excludeAppointmentId} is
     * the appointment being updated (never itself a conflict with its own unchanged
     * slot) — {@code null} when creating.
     */
    private void throwIfDoctorDoubleBooked(String doctorId, LocalDateTime requestedDate, String excludeAppointmentId) {
        List<Appointment> doctorAppointments = new ArrayList<>(
                appointmentRepository.findByDoctor_DoctorIdAndDeletedAtIsNull(doctorId));
        if (excludeAppointmentId != null) {
            doctorAppointments.removeIf(a -> a.getAppointmentId().equals(excludeAppointmentId));
        }
        List<Appointment> sorted = self.sort(doctorAppointments, Comparator.comparing(Appointment::getAppointmentDate));
        if (self.search(sorted, requestedDate, Appointment::getAppointmentDate) != -1) {
            throw new ConflictException("Doctor already has an appointment scheduled at this date and time");
        }
    }

    /**
     * AOP entry point for {@code AlgorithmAspect} — sorts {@code list} in place and
     * returns the same reference; {@code list} must be mutable. Must be called via
     * {@link #self}, never as {@code this.sort(...)}: Spring AOP proxies only intercept
     * calls made through the proxy, so a same-class call would bypass the aspect and
     * fall through to the body below.
     */
    @ApplyAlgorithm("mergeSort")
    public <T> List<T> sort(List<T> list, Comparator<T> comparator) {
        throw new IllegalStateException("AlgorithmAspect did not intercept this call");
    }

    /**
     * AOP entry point for {@code AlgorithmAspect} — {@code list} must already be sorted
     * by the same key {@code keyExtractor} produces (see {@link #sort} above); binary
     * search on an unsorted list gives a meaningless result, not an error. Must be
     * called via {@link #self}, never as {@code this.search(...)}.
     */
    @ApplyAlgorithm("binarySearch")
    public <T> int search(List<T> list, Object targetKey, Function<T, ?> keyExtractor) {
        throw new IllegalStateException("AlgorithmAspect did not intercept this call");
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
