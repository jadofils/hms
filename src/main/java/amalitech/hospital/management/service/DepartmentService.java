package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.SqlQueryBuilder;
import amalitech.hospital.management.dto.doctor.DepartmentDoctorCountResponse;
import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.PatchDepartmentRequest;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Department;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.repository.doctor.DepartmentRepository;
import amalitech.hospital.management.utils.PageableDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Department CRUD.
 *
 * Single-item lookups are cached in Redis under the "departments" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @SqlQueryBuilder}-annotated method through the Spring AOP proxy — see
     * {@link #findDepartmentsWithDoctorCounts}. {@code @Lazy} breaks the circular
     * dependency this creates at bean-creation time.
     */
    @Lazy
    private final DepartmentService self;

    // Defaults to name ASC (matching this endpoint's own Swagger sort example) when
    // the caller sends no ?sort= at all — see PageableDefaults' own Javadoc.
    public PagedModel<DepartmentResponse> getDepartments(Pageable pageable) {
        Pageable sorted = PageableDefaults.withDefaultSort(pageable, "name", Sort.Direction.ASC);
        return new PagedModel<>(departmentRepository.findAll(sorted).map(this::toResponse));
    }

    /**
     * Every department with at least one active doctor, plus the count — an admin
     * staffing overview, distinct from {@link #getDepartments} (every department,
     * staffed or not) or {@link #getDepartmentDoctors} (one department's full doctor
     * list). Backed by a {@code GROUP BY}/{@code HAVING} native query (see
     * {@code SqlQueryBuilderAspect}'s {@code "findDepartmentsWithDoctors"} case).
     */
    public List<DepartmentDoctorCountResponse> getStaffingSummary() {
        return self.findDepartmentsWithDoctorCounts().stream()
                .map(row -> {
                    DepartmentDoctorCountResponse response = new DepartmentDoctorCountResponse();
                    response.setDepartmentId((String) row[0]);
                    response.setName((String) row[1]);
                    response.setDoctorCount(((Number) row[2]).longValue());
                    return response;
                })
                .toList();
    }

    /**
     * AOP entry point for {@code SqlQueryBuilderAspect} — must be called via
     * {@link #self}, never as {@code this.findDepartmentsWithDoctorCounts()}: Spring AOP
     * proxies only intercept calls made through the proxy, so a same-class call would
     * bypass the aspect and fall through to the body below.
     */
    @SqlQueryBuilder("findDepartmentsWithDoctors")
    public List<Object[]> findDepartmentsWithDoctorCounts() {
        throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
    }

    /** Not populated by {@link #getDepartments} or by create/update — only by this
     *  single-item lookup, same convention as {@code DoctorService.getDoctor}. Reuses
     *  {@link #getDepartmentDoctors}, the same method the dedicated
     *  {@code GET /departments/{id}/doctors} sub-resource endpoint already calls. */
    @Cacheable(value = "departments", key = "#departmentId")
    public DepartmentResponse getDepartment(String departmentId) {
        DepartmentResponse response = toResponse(findDepartmentOrThrow(departmentId));
        response.setDoctors(getDepartmentDoctors(departmentId));
        return response;
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new ConflictException("Department '" + request.getName() + "' already exists");
        }
        if (request.getPhone() != null && departmentRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Department department = new Department();
        department.setName(request.getName());
        department.setLocation(request.getLocation());
        department.setPhone(request.getPhone());
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    @CachePut(value = "departments", key = "#departmentId")
    public DepartmentResponse updateDepartment(String departmentId, DepartmentRequest request) {
        Department department = findDepartmentOrThrow(departmentId);
        throwIfHeldByAnyDoctor(department, "updated");

        if (!department.getName().equals(request.getName())
                && departmentRepository.existsByName(request.getName())) {
            throw new ConflictException("Department '" + request.getName() + "' already exists");
        }
        if (request.getPhone() != null && !request.getPhone().equals(department.getPhone())
                && departmentRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        department.setName(request.getName());
        department.setLocation(request.getLocation());
        department.setPhone(request.getPhone());
        department.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(departmentRepository.save(department));
    }

    /**
     * Partial-update counterpart to {@link #updateDepartment} — only touches a field
     * when the request actually included it. The still-held-by-a-doctor guard only
     * fires when {@code name} is being changed — the same "only guard what's actually
     * changing" reasoning {@code RoleService.patchRole} uses, since renaming an
     * in-use department is the risky case, not touching its phone/location.
     */
    @Transactional
    @CachePut(value = "departments", key = "#departmentId")
    public DepartmentResponse patchDepartment(String departmentId, PatchDepartmentRequest patch) {
        Department department = findDepartmentOrThrow(departmentId);

        if (patch.getName() != null) {
            throwIfHeldByAnyDoctor(department, "updated");
            if (!department.getName().equals(patch.getName())
                    && departmentRepository.existsByName(patch.getName())) {
                throw new ConflictException("Department '" + patch.getName() + "' already exists");
            }
            department.setName(patch.getName());
        }
        if (patch.getLocation() != null) {
            department.setLocation(patch.getLocation());
        }
        if (patch.getPhone() != null) {
            if (!patch.getPhone().equals(department.getPhone())
                    && departmentRepository.existsByPhone(patch.getPhone())) {
                throw new ConflictException("Phone '" + patch.getPhone() + "' is already registered");
            }
            department.setPhone(patch.getPhone());
        }
        department.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    @CacheEvict(value = "departments", key = "#departmentId")
    public void deleteDepartment(String departmentId) {
        Department department = findDepartmentOrThrow(departmentId);
        throwIfHeldByAnyDoctor(department, "deleted");
        department.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        departmentRepository.save(department);
    }

    public List<DoctorResponse> getDepartmentDoctors(String departmentId) {
        Department department = findDepartmentOrThrow(departmentId);
        List<Doctor> doctors = department.getDoctors();
        if (doctors == null) {
            return List.of();
        }
        return doctors.stream()
                .filter(d -> d.getDeletedAt() == null)
                .map(this::toDoctorResponse)
                .toList();
    }

    /** A department still listed by at least one non-deleted doctor can't be renamed or
     *  removed out from under them — unassign it from every doctor first, then
     *  update/delete it once nobody actively lists it. */
    private void throwIfHeldByAnyDoctor(Department department, String action) {
        List<Doctor> doctors = department.getDoctors();
        boolean stillHeld = doctors != null && doctors.stream().anyMatch(d -> d.getDeletedAt() == null);
        if (stillHeld) {
            throw new ConflictException(
                    "Department cannot be " + action + " while it is still assigned to one or more doctors");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Department findDepartmentOrThrow(String departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department not found: " + departmentId));
        if (department.getDeletedAt() != null) {
            throw new NotFoundException("Department not found: " + departmentId);
        }
        return department;
    }

    private DepartmentResponse toResponse(Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setDepartmentId(department.getDepartmentId());
        response.setName(department.getName());
        response.setLocation(department.getLocation());
        response.setPhone(department.getPhone());
        return response;
    }

    /** Doesn't populate {@code departments} on the returned doctor — this is a roster
     *  view of one department, not a full doctor lookup (see {@code DoctorService.getDoctor}). */
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
