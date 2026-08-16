package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Department;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.repository.doctor.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    public PagedModel<DepartmentResponse> getDepartments(Pageable pageable) {
        return new PagedModel<>(departmentRepository.findAll(pageable).map(this::toResponse));
    }

    @Cacheable(value = "departments", key = "#departmentId")
    public DepartmentResponse getDepartment(String departmentId) {
        return toResponse(findDepartmentOrThrow(departmentId));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new ConflictException("Department '" + request.getName() + "' already exists");
        }
        if (request.getPhone() != null && departmentRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone '" + request.getPhone() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
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
        department.setUpdatedAt(LocalDateTime.now());
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    @CacheEvict(value = "departments", key = "#departmentId")
    public void deleteDepartment(String departmentId) {
        Department department = findDepartmentOrThrow(departmentId);
        throwIfHeldByAnyDoctor(department, "deleted");
        department.setDeletedAt(LocalDateTime.now());
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
