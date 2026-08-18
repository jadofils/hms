package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.annotation.SqlQueryBuilder;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorDepartmentRosterResponse;
import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Department;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.repository.doctor.DepartmentRepository;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Doctor CRUD + department membership.
 *
 * Single-item lookups are cached in Redis under the "doctors" cache; every write
 * invalidates the affected entry.
 *
 * <p>Every doctor must belong to at least one department — enforced at two points, not
 * one, since {@link #assignDepartment}/{@link #removeDepartment} can change membership
 * long after creation: {@link #createDoctor} rejects a request with no
 * {@code departmentIds} before persisting anything, and {@link #removeDepartment}
 * refuses to strip a doctor's last remaining department. Together these mean there is no
 * API path that can ever leave a doctor with zero departments once created.
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findDoctorsPage}. {@code @Lazy} breaks the circular dependency this creates
     * at bean-creation time.
     */
    @Lazy
    private final DoctorService self;

    /**
     * Listing is served through {@link #findDoctorsPage}, an {@code @FindUserData}-annotated
     * method (AOP-driven native SQL — see {@link amalitech.hospital.management.aop.FindUserDataAspect}),
     * the same pattern {@code UserService.getUsers}/{@code PatientService.getPatients} use.
     * Department membership isn't part of this listing (see the aspect's "doctor" case for
     * why) — only the single-item {@link #getDoctor} includes it.
     */
    public PagedModel<DoctorResponse> getDoctors(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;
        PagedRawResult raw = self.findDoctorsPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir);
        List<DoctorResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    DoctorResponse response = new DoctorResponse();
                    response.setDoctorId((String) cols[0]);
                    response.setFirstName((String) cols[1]);
                    response.setLastName((String) cols[2]);
                    response.setSpecialization((String) cols[3]);
                    response.setPhone((String) cols[4]);
                    response.setEmail((String) cols[5]);
                    return response;
                })
                .toList();
        Page<DoctorResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findDoctorsPage(...)}: Spring AOP proxies only intercept calls
     * made through the proxy, so a same-class call would bypass the aspect and fall
     * through to the body below.
     */
    @FindUserData(domain = "doctor")
    public PagedRawResult findDoctorsPage(int page, int size, String sortBy, String sortDir) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    @Cacheable(value = "doctors", key = "#doctorId")
    public DoctorResponse getDoctor(String doctorId) {
        return toResponse(findDoctorOrThrow(doctorId));
    }

    /**
     * One row per doctor-department pairing (a doctor in N departments appears N
     * times) — a flattened roster view, distinct from {@link #getDoctor} (one doctor,
     * departments nested underneath) or {@link #getDoctors} (every doctor, no
     * department info at all — see that method's own Javadoc for why). Backed by a
     * joined native query (see {@code SqlQueryBuilderAspect}'s
     * {@code "findDoctorsByDepartment"} case).
     */
    public List<DoctorDepartmentRosterResponse> getDoctorDepartmentRoster() {
        return self.findDoctorsByDepartment().stream()
                .map(row -> {
                    DoctorDepartmentRosterResponse response = new DoctorDepartmentRosterResponse();
                    response.setDoctorId((String) row[0]);
                    response.setFirstName((String) row[1]);
                    response.setLastName((String) row[2]);
                    response.setDepartment((String) row[3]);
                    return response;
                })
                .toList();
    }

    /**
     * AOP entry point for {@code SqlQueryBuilderAspect} — must be called via
     * {@link #self}, never as {@code this.findDoctorsByDepartment()}: Spring AOP
     * proxies only intercept calls made through the proxy, so a same-class call would
     * bypass the aspect and fall through to the body below.
     */
    @SqlQueryBuilder("findDoctorsByDepartment")
    public List<Object[]> findDoctorsByDepartment() {
        throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
    }

    /**
     * {@code request.getDepartmentIds()} must be non-empty — a doctor must belong
     * somewhere from the moment they're created (see this class's Javadoc). Each id is
     * granted via {@link #assignDepartment} in the same transaction as the save above,
     * so an unknown department id (or a request that manages to pass the same id twice)
     * rolls the whole creation back rather than leaving a half-assigned doctor behind —
     * the same atomicity {@code RoleService.createRole} uses for
     * {@code RoleRequest.permissionIds}.
     */
    @Transactional
    public DoctorResponse createDoctor(DoctorRequest request) {
        if (request.getDepartmentIds() == null || request.getDepartmentIds().isEmpty()) {
            throw new BadRequestException("A doctor must be assigned to at least one department");
        }
        if (request.getEmail() != null && doctorRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhone() != null && doctorRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Doctor doctor = new Doctor();
        doctor.setFirstName(request.getFirstName());
        doctor.setLastName(request.getLastName());
        doctor.setSpecialization(request.getSpecialization());
        doctor.setPhone(request.getPhone());
        doctor.setEmail(request.getEmail());
        doctor.setCreatedAt(now);
        doctor.setUpdatedAt(now);
        Doctor saved = doctorRepository.save(doctor);

        for (String departmentId : request.getDepartmentIds()) {
            // self.assignDepartment(...), not this.assignDepartment(...) — assignDepartment
            // is @Transactional/@CacheEvict, and a same-class call bypasses that proxy
            // advice too, not just the custom @FindUserData/@SqlQueryBuilder aspects this
            // self-injected field otherwise exists for.
            self.assignDepartment(saved.getDoctorId(), departmentId);
        }

        return toResponse(saved);
    }

    @Transactional
    @CachePut(value = "doctors", key = "#doctorId")
    public DoctorResponse updateDoctor(String doctorId, DoctorRequest request) {
        Doctor doctor = findDoctorOrThrow(doctorId);

        if (request.getEmail() != null && !request.getEmail().equals(doctor.getEmail())
                && doctorRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhone() != null && !request.getPhone().equals(doctor.getPhone())
                && doctorRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        doctor.setFirstName(request.getFirstName());
        doctor.setLastName(request.getLastName());
        doctor.setSpecialization(request.getSpecialization());
        doctor.setPhone(request.getPhone());
        doctor.setEmail(request.getEmail());
        doctor.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(doctorRepository.save(doctor));
    }

    @Transactional
    @CacheEvict(value = "doctors", key = "#doctorId")
    public void deleteDoctor(String doctorId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        doctor.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        doctorRepository.save(doctor);
    }

    // ── Department membership ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "doctors", key = "#doctorId")
    public void assignDepartment(String doctorId, String departmentId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        Department department = findDepartmentOrThrow(departmentId);

        List<Department> departments = doctor.getDepartments() == null ? new ArrayList<>() : doctor.getDepartments();
        if (departments.stream().anyMatch(d -> d.getDepartmentId().equals(departmentId))) {
            throw new ConflictException("Doctor is already assigned to this department");
        }
        departments.add(department);
        doctor.setDepartments(departments);
        doctorRepository.save(doctor);
    }

    /**
     * Refuses to remove a doctor's last remaining department (see this class's
     * Javadoc) — assign a replacement department first if the doctor is moving
     * elsewhere entirely.
     */
    @Transactional
    @CacheEvict(value = "doctors", key = "#doctorId")
    public void removeDepartment(String doctorId, String departmentId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        List<Department> departments = doctor.getDepartments();
        boolean isAssigned = departments != null
                && departments.stream().anyMatch(d -> d.getDepartmentId().equals(departmentId));
        if (!isAssigned) {
            throw new NotFoundException("Doctor is not assigned to this department");
        }
        if (departments.size() == 1) {
            throw new ConflictException(
                    "Doctor must remain assigned to at least one department — "
                            + "assign another department before removing the last one");
        }
        departments.removeIf(d -> d.getDepartmentId().equals(departmentId));
        doctorRepository.save(doctor);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Doctor findDoctorOrThrow(String doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
        if (doctor.getDeletedAt() != null) {
            throw new NotFoundException("Doctor not found: " + doctorId);
        }
        return doctor;
    }

    private Department findDepartmentOrThrow(String departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department not found: " + departmentId));
        if (department.getDeletedAt() != null) {
            throw new NotFoundException("Department not found: " + departmentId);
        }
        return department;
    }

    private DoctorResponse toResponse(Doctor doctor) {
        DoctorResponse response = new DoctorResponse();
        response.setDoctorId(doctor.getDoctorId());
        response.setFirstName(doctor.getFirstName());
        response.setLastName(doctor.getLastName());
        response.setSpecialization(doctor.getSpecialization());
        response.setPhone(doctor.getPhone());
        response.setEmail(doctor.getEmail());
        List<Department> departments = doctor.getDepartments();
        response.setDepartments(departments == null ? List.of() : departments.stream()
                .map(this::toDepartmentResponse)
                .toList());
        return response;
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setDepartmentId(department.getDepartmentId());
        response.setName(department.getName());
        response.setLocation(department.getLocation());
        response.setPhone(department.getPhone());
        return response;
    }
}
