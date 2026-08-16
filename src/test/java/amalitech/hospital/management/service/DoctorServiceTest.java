package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Department;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.repository.doctor.DepartmentRepository;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private DepartmentRepository departmentRepository;
    // Stands in for the self-injected AOP proxy reference — findDoctorsPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private DoctorService self;

    private DoctorService doctorService;

    private Doctor existingDoctor;
    private Department existingDepartment;

    @BeforeEach
    void setUp() {
        doctorService = new DoctorService(doctorRepository, departmentRepository, self);

        existingDoctor = new Doctor();
        existingDoctor.setDoctorId("doctor-1");
        existingDoctor.setFirstName("Greg");
        existingDoctor.setLastName("House");
        existingDoctor.setSpecialization("Diagnostics");
        existingDoctor.setPhone("1234567");
        existingDoctor.setEmail("house@example.com");
        existingDoctor.setDepartments(new ArrayList<>());

        existingDepartment = new Department();
        existingDepartment.setDepartmentId("dept-1");
        existingDepartment.setName("Diagnostics");
    }

    // ── getDoctors (AOP-driven pagination) ──────────────────────────────────

    @Test
    void getDoctors_mapsRawRowsAndTotalIntoPagedModel() {
        Object[] row = {"doctor-1", "Greg", "House", "Diagnostics", "1234567", "house@example.com"};
        when(self.findDoctorsPage(0, 20, null, null)).thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<DoctorResponse> result = doctorService.getDoctors(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        DoctorResponse response = result.getContent().get(0);
        assertThat(response.getDoctorId()).isEqualTo("doctor-1");
        assertThat(response.getFirstName()).isEqualTo("Greg");
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getDoctors_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findDoctorsPage(0, 20, "lastName", "DESC")).thenReturn(new PagedRawResult(List.of(), 0L));

        doctorService.getDoctors(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastName")));

        verify(self).findDoctorsPage(0, 20, "lastName", "DESC");
    }

    // ── getDoctor ────────────────────────────────────────────────────────────

    @Test
    void getDoctor_returnsMappedResponseIncludingDepartments_whenFoundAndActive() {
        existingDoctor.getDepartments().add(existingDepartment);
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));

        DoctorResponse response = doctorService.getDoctor("doctor-1");

        assertThat(response.getDoctorId()).isEqualTo("doctor-1");
        assertThat(response.getDepartments()).hasSize(1);
        assertThat(response.getDepartments().get(0).getName()).isEqualTo("Diagnostics");
    }

    @Test
    void getDoctor_throwsNotFound_whenAbsent() {
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getDoctor("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getDoctor_throwsNotFound_whenSoftDeleted() {
        existingDoctor.setDeletedAt(LocalDateTime.now());
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));

        assertThatThrownBy(() -> doctorService.getDoctor("doctor-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── createDoctor ─────────────────────────────────────────────────────────

    @Test
    void createDoctor_throwsConflict_whenEmailTaken() {
        DoctorRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(doctorRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createDoctor_throwsConflict_whenPhoneTaken() {
        DoctorRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(doctorRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(doctorRepository.existsByPhone("7654321")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createDoctor_savesSuccessfully() {
        DoctorRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = doctorService.createDoctor(request);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Bob");
    }

    // ── updateDoctor ─────────────────────────────────────────────────────────

    @Test
    void updateDoctor_throwsNotFound_whenAbsent() {
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());
        DoctorRequest request = requestFor("Greg", "house@example.com", "1234567");

        assertThatThrownBy(() -> doctorService.updateDoctor("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateDoctor_doesNotConflictCheck_whenEmailUnchanged() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));
        DoctorRequest request = requestFor("Greg", "house@example.com", "1234567");

        doctorService.updateDoctor("doctor-1", request);

        verify(doctorRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateDoctor_updatesFields() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.existsByEmail("wilson@example.com")).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));
        DoctorRequest request = requestFor("James", "wilson@example.com", "1234567");

        DoctorResponse response = doctorService.updateDoctor("doctor-1", request);

        assertThat(response.getFirstName()).isEqualTo("James");
        assertThat(existingDoctor.getEmail()).isEqualTo("wilson@example.com");
    }

    @Test
    void updateDoctor_throwsConflict_whenEmailChangedToExistingOne() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.existsByEmail("taken@example.com")).thenReturn(true);
        DoctorRequest request = requestFor("Greg", "taken@example.com", "1234567");

        assertThatThrownBy(() -> doctorService.updateDoctor("doctor-1", request))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void updateDoctor_throwsConflict_whenPhoneChangedToExistingOne() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.existsByPhone("9998887")).thenReturn(true);
        DoctorRequest request = requestFor("Greg", "house@example.com", "9998887");

        assertThatThrownBy(() -> doctorService.updateDoctor("doctor-1", request))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any());
    }

    // ── deleteDoctor ─────────────────────────────────────────────────────────

    @Test
    void deleteDoctor_setsDeletedAt() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        doctorService.deleteDoctor("doctor-1");

        assertThat(existingDoctor.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteDoctor_throwsNotFound_whenAbsent() {
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.deleteDoctor("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── department membership ───────────────────────────────────────────────

    @Test
    void assignDepartment_throwsNotFound_whenDepartmentAbsent() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.assignDepartment("doctor-1", "dept-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void assignDepartment_throwsNotFound_whenDepartmentSoftDeleted() {
        existingDepartment.setDeletedAt(LocalDateTime.now());
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        assertThatThrownBy(() -> doctorService.assignDepartment("doctor-1", "dept-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void assignDepartment_throwsConflict_whenAlreadyAssigned() {
        existingDoctor.getDepartments().add(existingDepartment);
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));

        assertThatThrownBy(() -> doctorService.assignDepartment("doctor-1", "dept-1"))
                .isInstanceOf(ConflictException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void assignDepartment_addsDepartment_whenNotAlreadyAssigned() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existingDepartment));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        doctorService.assignDepartment("doctor-1", "dept-1");

        assertThat(existingDoctor.getDepartments()).contains(existingDepartment);
    }

    @Test
    void removeDepartment_throwsNotFound_whenNotAssigned() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));

        assertThatThrownBy(() -> doctorService.removeDepartment("doctor-1", "dept-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeDepartment_removesDepartment_whenAssigned() {
        existingDoctor.getDepartments().add(existingDepartment);
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        doctorService.removeDepartment("doctor-1", "dept-1");

        assertThat(existingDoctor.getDepartments()).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DoctorRequest requestFor(String firstName, String email, String phone) {
        DoctorRequest request = new DoctorRequest();
        request.setFirstName(firstName);
        request.setLastName("Doe");
        request.setSpecialization("General");
        request.setPhone(phone);
        request.setEmail(email);
        return request;
    }
}
