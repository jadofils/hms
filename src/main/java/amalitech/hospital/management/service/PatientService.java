package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.MedicalRecordResponse;
import amalitech.hospital.management.dto.patient.PatientAllergyResponse;
import amalitech.hospital.management.dto.patient.PatientFeedbackResponse;
import amalitech.hospital.management.dto.patient.PatientNoteResponse;
import amalitech.hospital.management.dto.patient.PatchPatientRequest;
import amalitech.hospital.management.dto.patient.PatientRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.dto.patient.ReferralResponse;
import amalitech.hospital.management.dto.patient.VitalSignResponse;
import amalitech.hospital.management.enums.Gender;
import amalitech.hospital.management.enums.PatientStatus;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.finance.Invoice;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.MedicalRecord;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.model.patient.PatientAllergy;
import amalitech.hospital.management.model.patient.PatientFeedback;
import amalitech.hospital.management.model.patient.PatientNote;
import amalitech.hospital.management.model.patient.Referral;
import amalitech.hospital.management.model.patient.VitalSign;
import amalitech.hospital.management.repository.finance.InvoiceRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.patient.MedicalRecordRepository;
import amalitech.hospital.management.repository.patient.PatientAllergyRepository;
import amalitech.hospital.management.repository.patient.PatientFeedbackRepository;
import amalitech.hospital.management.repository.patient.PatientNoteRepository;
import amalitech.hospital.management.repository.patient.PatientRepository;
import amalitech.hospital.management.repository.patient.ReferralRepository;
import amalitech.hospital.management.repository.patient.VitalSignRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Patient CRUD.
 *
 * Single-item lookups are cached in Redis under the "patients" cache (see
 * {@link amalitech.hospital.management.config.CacheConfig}); every write invalidates
 * the affected entry so a stale patient is never served after an update/delete.
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    // Read directly at the repository level (not through AppointmentService/
    // InvoiceService) — the same "reach into a repository, not another service" style
    // DoctorService already uses for DepartmentRepository, and it avoids any risk of a
    // circular service dependency for what's a purely additive, read-only eager load
    // (see getPatient's Javadoc).
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final PatientFeedbackRepository patientFeedbackRepository;
    private final PatientNoteRepository patientNoteRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VitalSignRepository vitalSignRepository;
    private final ReferralRepository referralRepository;

    // Backs getPatient's CompletableFuture fan-out (HMS v5) — @Qualifier since a plain
    // Executor-typed field would otherwise be ambiguous against AsyncConfig's other bean,
    // mailTaskExecutor.
    @Qualifier("patientProfileExecutor")
    private final Executor patientProfileExecutor;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findPatientsPage}. {@code @Lazy} breaks the circular dependency this creates
     * at bean-creation time. Also used (HMS v5) to dispatch {@link #getPatient}'s 9
     * {@code @Transactional} fetch methods so each one runs through the real proxy, not
     * a same-class {@code this.} call the proxy would never see.
     */
    @Lazy
    private final PatientService self;

    // Single-flight dedup for getPatient's cache-miss path (HMS v5) — @Cacheable above
    // has no sync=true, so without this, N concurrent requests for the same never-cached
    // patientId would each independently launch their own 9-way CompletableFuture
    // fan-out. Entries are removed as soon as their future completes (success or
    // failure) — see startPatientProfileFetch.
    private final ConcurrentHashMap<String, CompletableFuture<PatientResponse>> inFlightPatientFetches =
            new ConcurrentHashMap<>();

    /**
     * Listing is served through {@link #findPatientsPage}, an {@code @FindUserData}-annotated
     * method (AOP-driven native SQL — see {@link amalitech.hospital.management.aop.FindUserDataAspect}),
     * the same pattern {@code UserService.getUsers} uses.
     *
     * A frontend column sort (Spring's standard {@code ?sort=property,direction} query
     * param, already bound onto {@code pageable}) is passed through as plain strings; only
     * the first {@code Sort.Order} is honored today. {@code FindUserDataAspect} validates
     * the column against this domain's own SELECT list before it ever reaches the query.
     *
     * An optional {@code status}/{@code gender} filter is validated against
     * {@link PatientStatus}/{@link Gender}'s own allowed values first — only an
     * already-validated enum {@code dbValue} is ever concatenated into the query, mirroring
     * the safety the sort-column whitelist already relies on.
     *
     * <p>{@code minAge}, when given, wins over {@code status}/{@code gender} and bypasses
     * {@code findPatientsPage} (the {@code @FindUserData}-driven listing) entirely in favor
     * of {@link PatientRepository#findByMinAgeNative} — a genuinely native SQL query, not
     * combinable with the other two filters in one call the way
     * {@code MedicalInventoryService.getInventoryRecords}' {@code lowStock}/{@code medicationId}
     * aren't either.
     */
    public PagedModel<PatientResponse> getPatients(Pageable pageable, String status, String gender, Integer minAge) {
        if (minAge != null) {
            Page<PatientResponse> page = patientRepository.findByMinAgeNative(minAge, pageable).map(this::toResponse);
            return new PagedModel<>(page);
        }

        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;

        String statusFilter = status == null || status.isBlank() ? null : validateStatus(status).getDbValue();
        String genderFilter = gender == null || gender.isBlank() ? null : validateGender(gender).getDbValue();

        PagedRawResult raw = self.findPatientsPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir,
                statusFilter, genderFilter);
        List<PatientResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    PatientResponse response = new PatientResponse();
                    response.setPatientId((String) cols[0]);
                    response.setFirstName((String) cols[1]);
                    response.setLastName((String) cols[2]);
                    response.setDob(cols[3] instanceof java.time.LocalDate d ? d : null);
                    response.setGender((String) cols[4]);
                    response.setPhone((String) cols[5]);
                    response.setEmail((String) cols[6]);
                    response.setAddress((String) cols[7]);
                    response.setStatus((String) cols[8]);
                    return response;
                })
                .toList();
        Page<PatientResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findPatientsPage(...)}: Spring AOP proxies only intercept calls
     * made through the proxy, so a same-class call would bypass the aspect and fall
     * through to the body below.
     */
    @FindUserData(domain = "patient")
    public PagedRawResult findPatientsPage(int page, int size, String sortBy, String sortDir,
                                            String status, String gender) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    /**
     * Eager-loads every piece of data actually linked to this patient — see
     * {@code PatientResponse}'s Javadoc. Not populated by {@link #getPatients} or by
     * create/update, same convention as {@code DoctorService.getDoctor}/
     * {@code UserService.getUser}/{@code RoleService.getRole}.
     *
     * <p>HMS v5 — the 9 independent lookups below (the core patient row plus 8
     * associated collections) used to run sequentially; each is now dispatched in
     * parallel via {@link #patientProfileExecutor}, joined once all 9 complete. Real
     * before/after latency: see {@code docs/patient-profile-performance-report.md}
     * ({@code PatientProfileBenchmarkTest}). Was never {@code @Transactional} itself
     * (each repository call always ran as its own short-lived Spring-Data-managed
     * transaction, sequentially) — parallelizing changes no existing transactional
     * guarantee, it just runs those same 9 independent transactions concurrently
     * instead of one after another.
     *
     * <p>{@link #inFlightPatientFetches} dedupes concurrent cache <em>misses</em> for
     * the same {@code patientId} — see that field's own Javadoc for why.
     */
    @Cacheable(value = "patients", key = "#patientId")
    public PatientResponse getPatient(String patientId) {
        CompletableFuture<PatientResponse> future =
                inFlightPatientFetches.computeIfAbsent(patientId, this::startPatientProfileFetch);
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /** Kicks off {@link #fetchPatientProfile} and arranges for its map entry to be
     *  removed the moment it completes, success or failure — never left behind to
     *  serve a stale in-flight reference to the next, unrelated cache-miss request for
     *  the same id. */
    private CompletableFuture<PatientResponse> startPatientProfileFetch(String patientId) {
        CompletableFuture<PatientResponse> future = fetchPatientProfile(patientId);
        future.whenComplete((response, ex) -> inFlightPatientFetches.remove(patientId));
        return future;
    }

    /** The actual 9-way fan-out — each fetch dispatched through {@link #self} so its own
     *  {@code @Transactional(readOnly = true)} runs through the real Spring proxy, not a
     *  same-class {@code this.} call the proxy would never see (same reasoning as
     *  {@link #findPatientsPage}'s self-injection). Every finder each of these 9 methods
     *  calls carries its own {@code @EntityGraph} (see the matching repository), so no
     *  lazy load happens after the query returns — the explicit {@code @Transactional}
     *  here is a correctness boundary independent of that, not a workaround for it. */
    private CompletableFuture<PatientResponse> fetchPatientProfile(String patientId) {
        CompletableFuture<PatientResponse> coreFuture =
                CompletableFuture.supplyAsync(() -> self.fetchPatientCore(patientId), patientProfileExecutor);
        CompletableFuture<List<AppointmentResponse>> appointmentsFuture =
                CompletableFuture.supplyAsync(() -> self.fetchAppointments(patientId), patientProfileExecutor);
        CompletableFuture<List<InvoiceResponse>> invoicesFuture =
                CompletableFuture.supplyAsync(() -> self.fetchInvoices(patientId), patientProfileExecutor);
        CompletableFuture<List<PatientAllergyResponse>> allergiesFuture =
                CompletableFuture.supplyAsync(() -> self.fetchAllergies(patientId), patientProfileExecutor);
        CompletableFuture<List<PatientFeedbackResponse>> feedbackFuture =
                CompletableFuture.supplyAsync(() -> self.fetchFeedback(patientId), patientProfileExecutor);
        CompletableFuture<List<PatientNoteResponse>> notesFuture =
                CompletableFuture.supplyAsync(() -> self.fetchNotes(patientId), patientProfileExecutor);
        CompletableFuture<List<MedicalRecordResponse>> medicalRecordsFuture =
                CompletableFuture.supplyAsync(() -> self.fetchMedicalRecords(patientId), patientProfileExecutor);
        CompletableFuture<List<VitalSignResponse>> vitalSignsFuture =
                CompletableFuture.supplyAsync(() -> self.fetchVitalSigns(patientId), patientProfileExecutor);
        CompletableFuture<List<ReferralResponse>> referralsFuture =
                CompletableFuture.supplyAsync(() -> self.fetchReferrals(patientId), patientProfileExecutor);

        return CompletableFuture.allOf(coreFuture, appointmentsFuture, invoicesFuture, allergiesFuture,
                        feedbackFuture, notesFuture, medicalRecordsFuture, vitalSignsFuture, referralsFuture)
                .thenApply(v -> {
                    PatientResponse response = coreFuture.join();
                    response.setAppointments(appointmentsFuture.join());
                    response.setInvoices(invoicesFuture.join());
                    response.setAllergies(allergiesFuture.join());
                    response.setFeedback(feedbackFuture.join());
                    response.setNotes(notesFuture.join());
                    response.setMedicalRecords(medicalRecordsFuture.join());
                    response.setVitalSigns(vitalSignsFuture.join());
                    response.setReferrals(referralsFuture.join());
                    return response;
                });
    }

    // ── getPatient's 9 parallel fetch units (HMS v5) ────────────────────────────
    // Each called only via self.fetchXxx(...) from fetchPatientProfile above, never
    // this.fetchXxx(...) — see that method's own Javadoc.

    @Transactional(readOnly = true)
    public PatientResponse fetchPatientCore(String patientId) {
        return toResponse(findPatientOrThrow(patientId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> fetchAppointments(String patientId) {
        return appointmentRepository.findByPatient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toAppointmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> fetchInvoices(String patientId) {
        return invoiceRepository.findByPatient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toInvoiceResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PatientAllergyResponse> fetchAllergies(String patientId) {
        return patientAllergyRepository.findByPatient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toAllergyResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PatientFeedbackResponse> fetchFeedback(String patientId) {
        return patientFeedbackRepository.findByPatient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toFeedbackResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PatientNoteResponse> fetchNotes(String patientId) {
        return patientNoteRepository.findByPatient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toNoteResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> fetchMedicalRecords(String patientId) {
        return medicalRecordRepository.findByAppointment_Patient_PatientIdAndDeletedAtIsNull(patientId).stream()
                .map(this::toMedicalRecordResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<VitalSignResponse> fetchVitalSigns(String patientId) {
        return vitalSignRepository.findByAppointment_Patient_PatientIdAndDeletedAtIsNull(patientId)
                .stream().map(this::toVitalSignResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReferralResponse> fetchReferrals(String patientId) {
        return referralRepository.findByAppointment_Patient_PatientIdAndDeletedAtIsNull(patientId)
                .stream().map(this::toReferralResponse).toList();
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        if (request.getEmail() != null && patientRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhone() != null && patientRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Patient patient = new Patient();
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDob(request.getDob());
        patient.setGender(validateGender(request.getGender()));
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? PatientStatus.ACTIVE : validateStatus(request.getStatus()));
        patient.setCreatedAt(now);
        patient.setUpdatedAt(now);
        return toResponse(patientRepository.save(patient));
    }

    @Transactional
    @CachePut(value = "patients", key = "#patientId")
    public PatientResponse updatePatient(String patientId, PatientRequest request) {
        Patient patient = findPatientOrThrow(patientId);

        if (request.getEmail() != null && !request.getEmail().equals(patient.getEmail())
                && patientRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhone() != null && !request.getPhone().equals(patient.getPhone())
                && patientRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDob(request.getDob());
        patient.setGender(validateGender(request.getGender()));
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            patient.setStatus(validateStatus(request.getStatus()));
        }
        patient.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(patientRepository.save(patient));
    }

    /**
     * Partial-update counterpart to {@link #updatePatient} — only the fields actually
     * present in {@code patch} are changed; everything else on the existing patient is
     * left untouched.
     */
    @Transactional
    @CachePut(value = "patients", key = "#patientId")
    public PatientResponse patchPatient(String patientId, PatchPatientRequest patch) {
        Patient patient = findPatientOrThrow(patientId);

        if (patch.getEmail() != null && !patch.getEmail().equals(patient.getEmail())
                && patientRepository.existsByEmail(patch.getEmail())) {
            throw new ConflictException("Email '" + patch.getEmail() + "' is already registered");
        }
        if (patch.getPhone() != null && !patch.getPhone().equals(patient.getPhone())
                && patientRepository.existsByPhone(patch.getPhone())) {
            throw new ConflictException("Phone '" + patch.getPhone() + "' is already registered");
        }

        if (patch.getFirstName() != null) {
            patient.setFirstName(patch.getFirstName());
        }
        if (patch.getLastName() != null) {
            patient.setLastName(patch.getLastName());
        }
        if (patch.getDob() != null) {
            patient.setDob(patch.getDob());
        }
        if (patch.getGender() != null) {
            patient.setGender(validateGender(patch.getGender()));
        }
        if (patch.getPhone() != null) {
            patient.setPhone(patch.getPhone());
        }
        if (patch.getEmail() != null) {
            patient.setEmail(patch.getEmail());
        }
        if (patch.getAddress() != null) {
            patient.setAddress(patch.getAddress());
        }
        if (patch.getStatus() != null && !patch.getStatus().isBlank()) {
            patient.setStatus(validateStatus(patch.getStatus()));
        }
        patient.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(patientRepository.save(patient));
    }

    @Transactional
    @CacheEvict(value = "patients", key = "#patientId")
    public void deletePatient(String patientId) {
        Patient patient = findPatientOrThrow(patientId);
        patient.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        patientRepository.save(patient);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Patient findPatientOrThrow(String patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + patientId));
        if (patient.getDeletedAt() != null) {
            throw new NotFoundException("Patient not found: " + patientId);
        }
        return patient;
    }

    /**
     * The DTO's own {@code @Pattern} already constrains this to an allowed value, so
     * {@link Gender#fromDbValue} should never actually throw here — this is defense in
     * depth, not the primary validation path.
     */
    private Gender validateGender(String gender) {
        try {
            return Gender.fromDbValue(gender);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** See {@link #validateGender} — same defense-in-depth reasoning. */
    private PatientStatus validateStatus(String status) {
        try {
            return PatientStatus.fromDbValue(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private PatientResponse toResponse(Patient patient) {
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

    // ── Eager-loaded related data (getPatient only) ─────────────────────────────
    // Mirrors AppointmentService/InvoiceService's own flattening style — id + a
    // "firstName lastName" scalar for each related person, not a nested response
    // object, matching how those two services already represent the same relationships.

    private AppointmentResponse toAppointmentResponse(Appointment appointment) {
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

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
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

    private PatientAllergyResponse toAllergyResponse(PatientAllergy allergy) {
        PatientAllergyResponse response = new PatientAllergyResponse();
        response.setAllergyId(allergy.getAllergyId());
        response.setAllergen(allergy.getAllergen());
        response.setReaction(allergy.getReaction());
        response.setSeverity(allergy.getSeverity());
        response.setCreatedAt(allergy.getCreatedAt());
        return response;
    }

    private PatientFeedbackResponse toFeedbackResponse(PatientFeedback feedback) {
        PatientFeedbackResponse response = new PatientFeedbackResponse();
        response.setFeedbackId(feedback.getFeedbackId());
        response.setAppointmentId(feedback.getAppointment() != null ? feedback.getAppointment().getAppointmentId() : null);
        response.setSubmittedBy(feedback.getSubmittedBy());
        response.setRating(feedback.getRating());
        response.setComments(feedback.getComments());
        response.setDateSubmitted(feedback.getDateSubmitted());
        return response;
    }

    private PatientNoteResponse toNoteResponse(PatientNote note) {
        PatientNoteResponse response = new PatientNoteResponse();
        response.setNoteId(note.getNoteId());
        response.setAppointmentId(note.getAppointment() != null ? note.getAppointment().getAppointmentId() : null);
        if (note.getAuthor() != null) {
            response.setAuthorUserId(note.getAuthor().getUserId());
            response.setAuthorUsername(note.getAuthor().getUsername());
        }
        response.setAuthorRole(note.getAuthorRole());
        response.setNoteText(note.getNoteText());
        response.setSource(note.getSource());
        response.setCreatedAt(note.getCreatedAt());
        return response;
    }

    private MedicalRecordResponse toMedicalRecordResponse(MedicalRecord record) {
        MedicalRecordResponse response = new MedicalRecordResponse();
        response.setRecordId(record.getRecordId());
        response.setAppointmentId(record.getAppointment().getAppointmentId());
        response.setDiagnosis(record.getDiagnosis());
        response.setSymptoms(record.getSymptoms());
        response.setNotes(record.getNotes());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private VitalSignResponse toVitalSignResponse(VitalSign vital) {
        VitalSignResponse response = new VitalSignResponse();
        response.setVitalId(vital.getVitalId());
        response.setAppointmentId(vital.getAppointment().getAppointmentId());
        response.setBloodPressureSystolic(vital.getBloodPressureSystolic());
        response.setBloodPressureDiastolic(vital.getBloodPressureDiastolic());
        response.setHeartRate(vital.getHeartRate());
        response.setTemperatureCelsius(vital.getTemperatureCelsius());
        response.setWeightKg(vital.getWeightKg());
        response.setHeightCm(vital.getHeightCm());
        response.setRecordedAt(vital.getRecordedAt());
        return response;
    }

    private ReferralResponse toReferralResponse(Referral referral) {
        ReferralResponse response = new ReferralResponse();
        response.setReferralId(referral.getReferralId());
        response.setAppointmentId(referral.getAppointment().getAppointmentId());
        response.setReferringDoctorId(referral.getReferringDoctor().getDoctorId());
        response.setReferringDoctorName(referral.getReferringDoctor().getFirstName() + " "
                + referral.getReferringDoctor().getLastName());
        response.setReferredToDoctorId(referral.getReferredToDoctor().getDoctorId());
        response.setReferredToDoctorName(referral.getReferredToDoctor().getFirstName() + " "
                + referral.getReferredToDoctor().getLastName());
        response.setReason(referral.getReason());
        response.setStatus(referral.getStatus());
        response.setCreatedAt(referral.getCreatedAt());
        return response;
    }
}
