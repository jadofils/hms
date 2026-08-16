package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.dto.patient.PatientRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.enums.Gender;
import amalitech.hospital.management.enums.PatientStatus;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.patient.Patient;
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

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findPatientsPage}. {@code @Lazy} breaks the circular dependency this creates
     * at bean-creation time.
     */
    @Lazy
    private final PatientService self;

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
     */
    public PagedModel<PatientResponse> getPatients(Pageable pageable, String status, String gender) {
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

    @Cacheable(value = "patients", key = "#patientId")
    public PatientResponse getPatient(String patientId) {
        return toResponse(findPatientOrThrow(patientId));
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        if (request.getEmail() != null && patientRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhone() != null && patientRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
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
        patient.setUpdatedAt(LocalDateTime.now());
        return toResponse(patientRepository.save(patient));
    }

    @Transactional
    @CacheEvict(value = "patients", key = "#patientId")
    public void deletePatient(String patientId) {
        Patient patient = findPatientOrThrow(patientId);
        patient.setDeletedAt(LocalDateTime.now());
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
}
