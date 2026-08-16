package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.patient.AppointmentRequest;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private EventBus eventBus;
    // Stands in for the self-injected AOP proxy reference — findAppointmentsPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private AppointmentService self;

    private AppointmentService appointmentService;

    private Patient existingPatient;
    private Doctor existingDoctor;
    private Appointment existingAppointment;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepository, patientRepository, doctorRepository, eventBus, self);

        existingPatient = new Patient();
        existingPatient.setPatientId("patient-1");
        existingPatient.setFirstName("Alice");
        existingPatient.setLastName("Doe");

        existingDoctor = new Doctor();
        existingDoctor.setDoctorId("doctor-1");
        existingDoctor.setFirstName("Greg");
        existingDoctor.setLastName("House");

        existingAppointment = new Appointment();
        existingAppointment.setAppointmentId("appt-1");
        existingAppointment.setPatient(existingPatient);
        existingAppointment.setDoctor(existingDoctor);
        existingAppointment.setAppointmentDate(LocalDateTime.now().plusDays(1));
        existingAppointment.setStatus(AppointmentStatus.SCHEDULED);
        existingAppointment.setReason("Checkup");
    }

    // ── getAppointments (AOP-driven pagination) ─────────────────────────────

    @Test
    void getAppointments_mapsRawRowsAndTotalIntoPagedModel() {
        Timestamp date = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 10, 0));
        Object[] row = {"appt-1", "patient-1", "doctor-1", "Alice", "Doe", "Greg", "House",
                date, "scheduled", "Checkup"};
        when(self.findAppointmentsPage(0, 20, null, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<AppointmentResponse> result = appointmentService.getAppointments(PageRequest.of(0, 20), null);

        assertThat(result.getContent()).hasSize(1);
        AppointmentResponse response = result.getContent().get(0);
        assertThat(response.getAppointmentId()).isEqualTo("appt-1");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getDoctorName()).isEqualTo("Greg House");
        assertThat(response.getStatus()).isEqualTo("scheduled");
        assertThat(response.getAppointmentDate()).isEqualTo(date.toLocalDateTime());
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getAppointments_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findAppointmentsPage(0, 20, "appointmentDate", "DESC", null))
                .thenReturn(new PagedRawResult(List.of(), 0L));
        Pageable sorted = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "appointmentDate"));

        appointmentService.getAppointments(sorted, null);

        verify(self).findAppointmentsPage(0, 20, "appointmentDate", "DESC", null);
    }

    @Test
    void getAppointments_validatesAndPassesStatusFilter() {
        when(self.findAppointmentsPage(0, 20, null, null, "completed"))
                .thenReturn(new PagedRawResult(List.of(), 0L));

        appointmentService.getAppointments(PageRequest.of(0, 20), "Completed");

        verify(self).findAppointmentsPage(0, 20, null, null, "completed");
    }

    @Test
    void getAppointments_throwsBadRequest_whenStatusFilterInvalid() {
        assertThatThrownBy(() -> appointmentService.getAppointments(PageRequest.of(0, 20), "bogus"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAppointments_acceptsLocalDateTimeDateColumn_onDriversThatReturnItDirectly() {
        LocalDateTime date = LocalDateTime.of(2026, 1, 1, 10, 0);
        Object[] row = {"appt-1", "patient-1", "doctor-1", "Alice", "Doe", "Greg", "House",
                date, "scheduled", "Checkup"};
        when(self.findAppointmentsPage(0, 20, null, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<AppointmentResponse> result = appointmentService.getAppointments(PageRequest.of(0, 20), null);

        assertThat(result.getContent().get(0).getAppointmentDate()).isEqualTo(date);
    }

    @Test
    void getAppointments_returnsNullDate_whenDateColumnIsAnUnrecognizedType() {
        Object[] row = {"appt-1", "patient-1", "doctor-1", "Alice", "Doe", "Greg", "House",
                "not-a-date", "scheduled", "Checkup"};
        when(self.findAppointmentsPage(0, 20, null, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<AppointmentResponse> result = appointmentService.getAppointments(PageRequest.of(0, 20), null);

        assertThat(result.getContent().get(0).getAppointmentDate()).isNull();
    }

    // ── getAppointment ───────────────────────────────────────────────────────

    @Test
    void getAppointment_returnsMappedResponse_whenFoundAndActive() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));

        AppointmentResponse response = appointmentService.getAppointment("appt-1");

        assertThat(response.getAppointmentId()).isEqualTo("appt-1");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getStatus()).isEqualTo("scheduled");
    }

    @Test
    void getAppointment_throwsNotFound_whenAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getAppointment("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAppointment_throwsNotFound_whenSoftDeleted() {
        existingAppointment.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));

        assertThatThrownBy(() -> appointmentService.getAppointment("appt-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── createAppointment ────────────────────────────────────────────────────

    @Test
    void createAppointment_throwsNotFound_whenPatientAbsent() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.empty());
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(NotFoundException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createAppointment_throwsNotFound_whenDoctorAbsent() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.empty());
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(NotFoundException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createAppointment_throwsNotFound_whenPatientSoftDeleted() {
        existingPatient.setDeletedAt(LocalDateTime.now());
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(NotFoundException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createAppointment_throwsNotFound_whenDoctorSoftDeleted() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        existingDoctor.setDeletedAt(LocalDateTime.now());
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(NotFoundException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createAppointment_savesWithDefaultScheduledStatus_whenStatusOmitted() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        AppointmentResponse response = appointmentService.createAppointment(request);

        assertThat(response.getStatus()).isEqualTo("scheduled");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getDoctorName()).isEqualTo("Greg House");
        verify(eventBus).publish(any(AppointmentCreatedEvent.class));
    }

    // ── updateAppointment ────────────────────────────────────────────────────

    @Test
    void updateAppointment_throwsNotFound_whenAppointmentAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());
        AppointmentRequest request = requestFor("patient-1", "doctor-1");

        assertThatThrownBy(() -> appointmentService.updateAppointment("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateAppointment_updatesFields() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        AppointmentRequest request = requestFor("patient-1", "doctor-1");
        request.setStatus("completed");
        request.setReason("Follow-up");

        AppointmentResponse response = appointmentService.updateAppointment("appt-1", request);

        assertThat(response.getStatus()).isEqualTo("completed");
        assertThat(existingAppointment.getReason()).isEqualTo("Follow-up");
    }

    // ── deleteAppointment ────────────────────────────────────────────────────

    @Test
    void deleteAppointment_setsDeletedAt() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        appointmentService.deleteAppointment("appt-1");

        assertThat(existingAppointment.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteAppointment_throwsNotFound_whenAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.deleteAppointment("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static AppointmentRequest requestFor(String patientId, String doctorId) {
        AppointmentRequest request = new AppointmentRequest();
        request.setPatientId(patientId);
        request.setDoctorId(doctorId);
        request.setAppointmentDate(LocalDateTime.now().plusDays(1));
        request.setReason("Checkup");
        return request;
    }
}
