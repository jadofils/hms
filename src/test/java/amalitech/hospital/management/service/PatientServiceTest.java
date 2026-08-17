package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.patient.PatientRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.enums.Gender;
import amalitech.hospital.management.enums.PatientStatus;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.model.patient.PatientAllergy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PatientAllergyRepository patientAllergyRepository;
    @Mock private PatientFeedbackRepository patientFeedbackRepository;
    @Mock private PatientNoteRepository patientNoteRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private ReferralRepository referralRepository;
    // Stands in for the self-injected AOP proxy reference — findPatientsPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private PatientService self;

    private PatientService patientService;

    private Patient existingPatient;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository, appointmentRepository, invoiceRepository,
                patientAllergyRepository, patientFeedbackRepository, patientNoteRepository,
                medicalRecordRepository, vitalSignRepository, referralRepository, self);

        existingPatient = new Patient();
        existingPatient.setPatientId("patient-1");
        existingPatient.setFirstName("Alice");
        existingPatient.setLastName("Doe");
        existingPatient.setDob(LocalDate.of(1990, 1, 1));
        existingPatient.setGender(Gender.F);
        existingPatient.setPhone("1234567");
        existingPatient.setEmail("alice@example.com");
        existingPatient.setAddress("123 Main St");
        existingPatient.setStatus(PatientStatus.ACTIVE);
    }

    // ── getPatients (AOP-driven pagination) ─────────────────────────────────

    @Test
    void getPatients_mapsRawRowsAndTotalIntoPagedModel() {
        Object[] row = {"patient-1", "Alice", "Doe", LocalDate.of(1990, 1, 1), "F", "1234567",
                "alice@example.com", "123 Main St", "active"};
        when(self.findPatientsPage(0, 20, null, null, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<PatientResponse> result = patientService.getPatients(PageRequest.of(0, 20), null, null);

        assertThat(result.getContent()).hasSize(1);
        PatientResponse response = result.getContent().get(0);
        assertThat(response.getPatientId()).isEqualTo("patient-1");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getGender()).isEqualTo("F");
        assertThat(response.getStatus()).isEqualTo("active");
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getPatients_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findPatientsPage(0, 20, "lastName", "DESC", null, null))
                .thenReturn(new PagedRawResult(List.of(), 0L));
        Pageable sorted = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastName"));

        patientService.getPatients(sorted, null, null);

        verify(self).findPatientsPage(0, 20, "lastName", "DESC", null, null);
    }

    @Test
    void getPatients_validatesAndPassesStatusAndGenderFilters() {
        when(self.findPatientsPage(0, 20, null, null, "active", "F"))
                .thenReturn(new PagedRawResult(List.of(), 0L));

        patientService.getPatients(PageRequest.of(0, 20), "Active", "f");

        verify(self).findPatientsPage(0, 20, null, null, "active", "F");
    }

    @Test
    void getPatients_throwsBadRequest_whenStatusFilterInvalid() {
        assertThatThrownBy(() -> patientService.getPatients(PageRequest.of(0, 20), "bogus", null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── getPatient ───────────────────────────────────────────────────────────

    @Test
    void getPatient_returnsMappedResponse_whenFoundAndActive() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));

        PatientResponse response = patientService.getPatient("patient-1");

        assertThat(response.getPatientId()).isEqualTo("patient-1");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getGender()).isEqualTo("F");
        // Unstubbed repositories default to an empty list (Mockito), not null — every
        // eager-loaded collection should still come back as an empty list, never null.
        assertThat(response.getAllergies()).isEmpty();
        assertThat(response.getAppointments()).isEmpty();
        assertThat(response.getInvoices()).isEmpty();
        assertThat(response.getFeedback()).isEmpty();
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getMedicalRecords()).isEmpty();
        assertThat(response.getVitalSigns()).isEmpty();
        assertThat(response.getReferrals()).isEmpty();
    }

    @Test
    void getPatient_eagerLoadsLinkedAllergies_unlikeThePaginatedListing() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        PatientAllergy allergy = new PatientAllergy();
        allergy.setAllergyId("allergy-1");
        allergy.setAllergen("Penicillin");
        allergy.setSeverity("severe");
        when(patientAllergyRepository.findByPatient_PatientIdAndDeletedAtIsNull("patient-1"))
                .thenReturn(List.of(allergy));

        PatientResponse response = patientService.getPatient("patient-1");

        assertThat(response.getAllergies()).hasSize(1);
        assertThat(response.getAllergies().get(0).getAllergen()).isEqualTo("Penicillin");
    }

    @Test
    void getPatient_throwsNotFound_whenAbsent() {
        when(patientRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPatient_throwsNotFound_whenSoftDeleted() {
        existingPatient.setDeletedAt(LocalDateTime.now());
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));

        assertThatThrownBy(() -> patientService.getPatient("patient-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── createPatient ────────────────────────────────────────────────────────

    @Test
    void createPatient_throwsConflict_whenEmailTaken() {
        PatientRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(patientRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> patientService.createPatient(request))
                .isInstanceOf(ConflictException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatient_throwsConflict_whenPhoneTaken() {
        PatientRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(patientRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(patientRepository.existsByPhone("7654321")).thenReturn(true);

        assertThatThrownBy(() -> patientService.createPatient(request))
                .isInstanceOf(ConflictException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatient_savesWithDefaultActiveStatus_whenStatusOmitted() {
        PatientRequest request = requestFor("Bob", "bob@example.com", "7654321");
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientResponse response = patientService.createPatient(request);

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        Patient saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(saved.getGender()).isEqualTo(Gender.M);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Bob");
    }

    @Test
    void createPatient_appliesExplicitStatus_whenProvided() {
        PatientRequest request = requestFor("Bob", "bob@example.com", "7654321");
        request.setStatus("inactive");
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientResponse response = patientService.createPatient(request);

        assertThat(response.getStatus()).isEqualTo("inactive");
    }

    @Test
    void createPatient_throwsBadRequest_whenGenderInvalid() {
        PatientRequest request = requestFor("Bob", "bob@example.com", "7654321");
        request.setGender("invalid-gender");

        assertThatThrownBy(() -> patientService.createPatient(request))
                .isInstanceOf(BadRequestException.class);
    }

    // ── updatePatient ────────────────────────────────────────────────────────

    @Test
    void updatePatient_throwsConflict_whenEmailChangedToExistingOne() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.existsByEmail("carol@example.com")).thenReturn(true);
        PatientRequest request = requestFor("Carol", "carol@example.com", "1234567");

        assertThatThrownBy(() -> patientService.updatePatient("patient-1", request))
                .isInstanceOf(ConflictException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void updatePatient_throwsConflict_whenPhoneChangedToExistingOne() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.existsByPhone("9998887")).thenReturn(true);
        PatientRequest request = requestFor("Alice", "alice@example.com", "9998887");

        assertThatThrownBy(() -> patientService.updatePatient("patient-1", request))
                .isInstanceOf(ConflictException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void updatePatient_appliesNewStatus_whenProvided() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        PatientRequest request = requestFor("Alice", "alice@example.com", "1234567");
        request.setStatus("inactive");

        PatientResponse response = patientService.updatePatient("patient-1", request);

        assertThat(response.getStatus()).isEqualTo("inactive");
    }

    @Test
    void updatePatient_throwsNotFound_whenAbsent() {
        when(patientRepository.findById("missing")).thenReturn(Optional.empty());
        PatientRequest request = requestFor("Alice", "alice@example.com", "1234567");

        assertThatThrownBy(() -> patientService.updatePatient("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePatient_doesNotConflictCheck_whenEmailUnchanged() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        PatientRequest request = requestFor("Alice", "alice@example.com", "1234567");

        patientService.updatePatient("patient-1", request);

        verify(patientRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updatePatient_updatesFields() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.existsByEmail("carol@example.com")).thenReturn(false);
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        PatientRequest request = requestFor("Carol", "carol@example.com", "1234567");

        PatientResponse response = patientService.updatePatient("patient-1", request);

        assertThat(response.getFirstName()).isEqualTo("Carol");
        assertThat(existingPatient.getEmail()).isEqualTo("carol@example.com");
    }

    // ── deletePatient ────────────────────────────────────────────────────────

    @Test
    void deletePatient_setsDeletedAt() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        patientService.deletePatient("patient-1");

        assertThat(existingPatient.getDeletedAt()).isNotNull();
    }

    @Test
    void deletePatient_throwsNotFound_whenAbsent() {
        when(patientRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.deletePatient("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static PatientRequest requestFor(String firstName, String email, String phone) {
        PatientRequest request = new PatientRequest();
        request.setFirstName(firstName);
        request.setLastName("Doe");
        request.setDob(LocalDate.of(1990, 1, 1));
        request.setGender("M");
        request.setPhone(phone);
        request.setEmail(email);
        request.setAddress("456 Side St");
        return request;
    }
}
