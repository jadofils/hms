package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DepartmentDoctorCountResponse;
import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Department;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.repository.doctor.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    // Stands in for the self-injected AOP proxy reference — findDepartmentsWithDoctorCounts
    // is @SqlQueryBuilder-annotated and normally intercepted by SqlQueryBuilderAspect;
    // mocked here at the boundary rather than exercised for real (see CLAUDE.md's Testing
    // section).
    @Mock private DepartmentService self;

    private DepartmentService departmentService;

    private Department existingDepartment;

    @BeforeEach
    void setUp() {
        departmentService = new DepartmentService(departmentRepository, self);

        existingDepartment = new Department();
        existingDepartment.setDepartmentId("dept-1");
        existingDepartment.setName("Cardiology");
        existingDepartment.setLocation("Building A");
        existingDepartment.setPhone("1234567");
        existingDepartment.setDoctors(new ArrayList<>());
    }

    // ── getDepartment ────────────────────────────────────────────────────────

    @Test
    void getDepartment_returnsMappedResponse_whenFoundAndActive() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        DepartmentResponse response = departmentService.getDepartment("dept-1");

        assertThat(response.getDepartmentId()).isEqualTo("dept-1");
        assertThat(response.getName()).isEqualTo("Cardiology");
    }

    @Test
    void getDepartment_throwsNotFound_whenAbsent() {
        when(departmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartment("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getDepartment_throwsNotFound_whenSoftDeleted() {
        existingDepartment.setDeletedAt(LocalDateTime.now());
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        assertThatThrownBy(() -> departmentService.getDepartment("dept-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── createDepartment ─────────────────────────────────────────────────────

    @Test
    void createDepartment_throwsConflict_whenNameTaken() {
        DepartmentRequest request = requestFor("Cardiology", "7654321");
        when(departmentRepository.existsByName("Cardiology")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(request))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createDepartment_throwsConflict_whenPhoneTaken() {
        DepartmentRequest request = requestFor("Oncology", "7654321");
        when(departmentRepository.existsByName("Oncology")).thenReturn(false);
        when(departmentRepository.existsByPhone("7654321")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(request))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createDepartment_savesSuccessfully() {
        DepartmentRequest request = requestFor("Oncology", "7654321");
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentResponse response = departmentService.createDepartment(request);

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(response.getName()).isEqualTo("Oncology");
    }

    // ── updateDepartment / deleteDepartment (in-use guard) ─────────────────

    @Test
    void updateDepartment_throwsConflict_whenStillAssignedToAnActiveDoctor() {
        existingDepartment.getDoctors().add(activeDoctor());
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        DepartmentRequest request = requestFor("Cardiology Renamed", "1234567");

        assertThatThrownBy(() -> departmentService.updateDepartment("dept-1", request))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_allowed_whenOnlyHeldByDeletedDoctors() {
        Doctor deletedDoctor = activeDoctor();
        deletedDoctor.setDeletedAt(LocalDateTime.now());
        existingDepartment.getDoctors().add(deletedDoctor);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        when(departmentRepository.existsByName("Cardiology Renamed")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        DepartmentRequest request = requestFor("Cardiology Renamed", "1234567");

        DepartmentResponse response = departmentService.updateDepartment("dept-1", request);

        assertThat(response.getName()).isEqualTo("Cardiology Renamed");
    }

    @Test
    void updateDepartment_throwsConflict_whenRenamedToExistingName() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        when(departmentRepository.existsByName("Oncology")).thenReturn(true);
        DepartmentRequest request = requestFor("Oncology", "1234567");

        assertThatThrownBy(() -> departmentService.updateDepartment("dept-1", request))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_throwsConflict_whenPhoneChangedToExistingOne() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        when(departmentRepository.existsByPhone("9998887")).thenReturn(true);
        DepartmentRequest request = requestFor("Cardiology", "9998887");

        assertThatThrownBy(() -> departmentService.updateDepartment("dept-1", request))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void deleteDepartment_throwsConflict_whenStillAssignedToAnActiveDoctor() {
        existingDepartment.getDoctors().add(activeDoctor());
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        assertThatThrownBy(() -> departmentService.deleteDepartment("dept-1"))
                .isInstanceOf(ConflictException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void deleteDepartment_setsDeletedAt_whenUnassigned() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        departmentService.deleteDepartment("dept-1");

        assertThat(existingDepartment.getDeletedAt()).isNotNull();
    }

    // ── getDepartmentDoctors ─────────────────────────────────────────────────

    @Test
    void getDepartmentDoctors_returnsOnlyActiveDoctors() {
        Doctor active = activeDoctor();
        Doctor deleted = activeDoctor();
        deleted.setDoctorId("doctor-2");
        deleted.setDeletedAt(LocalDateTime.now());
        existingDepartment.getDoctors().add(active);
        existingDepartment.getDoctors().add(deleted);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        List<DoctorResponse> doctors = departmentService.getDepartmentDoctors("dept-1");

        assertThat(doctors).hasSize(1);
        assertThat(doctors.get(0).getDoctorId()).isEqualTo("doctor-1");
    }

    @Test
    void getDepartmentDoctors_returnsEmptyList_whenDoctorsCollectionIsNull() {
        existingDepartment.setDoctors(null);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        List<DoctorResponse> doctors = departmentService.getDepartmentDoctors("dept-1");

        assertThat(doctors).isEmpty();
    }

    // ── getStaffingSummary (AOP-driven native query) ────────────────────────

    @Test
    void getStaffingSummary_mapsRawRowsIntoResponses() {
        Object[] row = {"dept-1", "Cardiology", 4L};
        when(self.findDepartmentsWithDoctorCounts()).thenReturn(List.<Object[]>of(row));

        List<DepartmentDoctorCountResponse> result = departmentService.getStaffingSummary();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDepartmentId()).isEqualTo("dept-1");
        assertThat(result.get(0).getName()).isEqualTo("Cardiology");
        assertThat(result.get(0).getDoctorCount()).isEqualTo(4L);
    }

    @Test
    void getStaffingSummary_returnsEmptyList_whenNoDepartmentIsStaffed() {
        when(self.findDepartmentsWithDoctorCounts()).thenReturn(List.of());

        assertThat(departmentService.getStaffingSummary()).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DepartmentRequest requestFor(String name, String phone) {
        DepartmentRequest request = new DepartmentRequest();
        request.setName(name);
        request.setLocation("Building A");
        request.setPhone(phone);
        return request;
    }

    private static Doctor activeDoctor() {
        Doctor doctor = new Doctor();
        doctor.setDoctorId("doctor-1");
        doctor.setFirstName("Greg");
        doctor.setLastName("House");
        return doctor;
    }
}
