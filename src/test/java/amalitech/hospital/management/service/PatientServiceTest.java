package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.patient.PatientAllergyResponse;
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
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
                medicalRecordRepository, vitalSignRepository, referralRepository,
                Runnable::run, self);

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

        PagedModel<PatientResponse> result = patientService.getPatients(PageRequest.of(0, 20), null, null, null);

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

        patientService.getPatients(sorted, null, null, null);

        verify(self).findPatientsPage(0, 20, "lastName", "DESC", null, null);
    }

    @Test
    void getPatients_validatesAndPassesStatusAndGenderFilters() {
        when(self.findPatientsPage(0, 20, null, null, "active", "F"))
                .thenReturn(new PagedRawResult(List.of(), 0L));

        patientService.getPatients(PageRequest.of(0, 20), "Active", "f", null);

        verify(self).findPatientsPage(0, 20, null, null, "active", "F");
    }

    @Test
    void getPatients_throwsBadRequest_whenStatusFilterInvalid() {
        assertThatThrownBy(() -> patientService.getPatients(PageRequest.of(0, 20), "bogus", null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getPatients_usesTheNativeMinAgeQuery_whenMinAgeGiven_bypassingFindPatientsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(patientRepository.findByMinAgeNative(65, pageable))
                .thenReturn(new PageImpl<>(List.of(existingPatient)));

        PagedModel<PatientResponse> result = patientService.getPatients(pageable, "active", "f", 65);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPatientId()).isEqualTo("patient-1");
        // minAge wins over status/gender — the @FindUserData-driven path is never touched.
        verify(self, never()).findPatientsPage(anyInt(), anyInt(), any(), any(), any(), any());
    }

    // ── getPatient (HMS v5 — orchestration only; self.fetchXxx is mocked, matching
    //    CLAUDE.md's self-injection testing convention) ───────────────────────────
    // The 9 fetch methods' own real logic (repository call + mapping, previously
    // exercised here directly) now has its own tests below, under "getPatient's fetch
    // methods" — getPatient itself is pure fan-out/join orchestration once self is mocked.

    @Test
    void getPatient_assemblesOneResponseFromAllNineParallelFetches() {
        PatientResponse core = new PatientResponse();
        core.setPatientId("patient-1");
        core.setFirstName("Alice");
        core.setGender("F");
        when(self.fetchPatientCore("patient-1")).thenReturn(core);
        PatientAllergyResponse allergy = new PatientAllergyResponse();
        allergy.setAllergen("Penicillin");
        when(self.fetchAllergies("patient-1")).thenReturn(List.of(allergy));
        // Every other self.fetchXxx("patient-1") call is left unstubbed — Mockito's
        // default answer for a List-returning method is an empty list, not null.

        PatientResponse response = patientService.getPatient("patient-1");

        assertThat(response.getPatientId()).isEqualTo("patient-1");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getGender()).isEqualTo("F");
        assertThat(response.getAllergies()).extracting("allergen").containsExactly("Penicillin");
        assertThat(response.getAppointments()).isEmpty();
        assertThat(response.getInvoices()).isEmpty();
        assertThat(response.getFeedback()).isEmpty();
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getMedicalRecords()).isEmpty();
        assertThat(response.getVitalSigns()).isEmpty();
        assertThat(response.getReferrals()).isEmpty();
    }

    @Test
    void getPatient_propagatesTheOriginalException_notACompletionExceptionWrapper() {
        when(self.fetchPatientCore("missing")).thenThrow(new NotFoundException("Patient not found: missing"));

        assertThatThrownBy(() -> patientService.getPatient("missing"))
                .isInstanceOf(NotFoundException.class)
                .isNotInstanceOf(java.util.concurrent.CompletionException.class);
    }

    @Test
    void getPatient_dedupesConcurrentMisses_forTheSamePatientId() throws Exception {
        // Real self-dispatch (not mocked) + a real fixed thread pool + a latch that
        // holds every caller at the same starting line — proves the ConcurrentHashMap
        // single-flight logic actually collapses N concurrent misses into one
        // underlying fetch, not just "happens to look that way" from running sequentially.
        PatientService realSelfService = new PatientService(patientRepository, appointmentRepository,
                invoiceRepository, patientAllergyRepository, patientFeedbackRepository, patientNoteRepository,
                medicalRecordRepository, vitalSignRepository, referralRepository,
                java.util.concurrent.Executors.newFixedThreadPool(8), null);
        setSelfTo(realSelfService, realSelfService);

        java.util.concurrent.CountDownLatch releaseAll = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger realFetchCount = new java.util.concurrent.atomic.AtomicInteger();
        when(patientRepository.findById("patient-1")).thenAnswer(inv -> {
            realFetchCount.incrementAndGet();
            releaseAll.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return Optional.of(existingPatient);
        });

        int callers = 6;
        java.util.concurrent.ExecutorService callerPool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        List<java.util.concurrent.Future<PatientResponse>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(callerPool.submit(() -> realSelfService.getPatient("patient-1")));
        }
        Thread.sleep(100); // let every caller reach computeIfAbsent before releasing the fetch
        releaseAll.countDown();

        for (java.util.concurrent.Future<PatientResponse> f : futures) {
            assertThat(f.get(2, java.util.concurrent.TimeUnit.SECONDS).getPatientId()).isEqualTo("patient-1");
        }
        callerPool.shutdown();
        // The whole point: 6 concurrent callers, but the underlying row lookup ran once.
        assertThat(realFetchCount.get()).isEqualTo(1);
    }

    @Test
    void getPatient_doesNotBlockADifferentPatientId_whileAnotherIsInFlight() throws Exception {
        PatientService realSelfService = new PatientService(patientRepository, appointmentRepository,
                invoiceRepository, patientAllergyRepository, patientFeedbackRepository, patientNoteRepository,
                medicalRecordRepository, vitalSignRepository, referralRepository,
                java.util.concurrent.Executors.newFixedThreadPool(8), null);
        setSelfTo(realSelfService, realSelfService);

        java.util.concurrent.CountDownLatch holdPatientOne = new java.util.concurrent.CountDownLatch(1);
        when(patientRepository.findById("patient-1")).thenAnswer(inv -> {
            holdPatientOne.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return Optional.of(existingPatient);
        });
        Patient otherPatient = new Patient();
        otherPatient.setPatientId("patient-2");
        otherPatient.setGender(Gender.M);
        otherPatient.setStatus(PatientStatus.ACTIVE);
        when(patientRepository.findById("patient-2")).thenReturn(Optional.of(otherPatient));

        java.util.concurrent.ExecutorService callerPool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<PatientResponse> blockedOne = callerPool.submit(() -> realSelfService.getPatient("patient-1"));
        // patient-2 must complete well before patient-1's latch is ever released.
        PatientResponse two = callerPool.submit(() -> realSelfService.getPatient("patient-2")).get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(two.getPatientId()).isEqualTo("patient-2");

        holdPatientOne.countDown();
        assertThat(blockedOne.get(2, java.util.concurrent.TimeUnit.SECONDS).getPatientId()).isEqualTo("patient-1");
        callerPool.shutdown();
    }

    /** Reassigns the {@code self} field via reflection — only the concurrency tests
     *  above need a real (non-mocked) self-reference; every other test keeps the
     *  standard mocked {@code self} set up in {@link #setUp}. */
    private static void setSelfTo(PatientService target, PatientService self) throws Exception {
        java.lang.reflect.Field field = PatientService.class.getDeclaredField("self");
        field.setAccessible(true);
        field.set(target, self);
    }

    // ── getPatient's fetch methods (real repository-mock-backed logic) ─────────

    @Test
    void fetchPatientCore_returnsMappedResponse_whenFoundAndActive() {
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));

        PatientResponse response = patientService.fetchPatientCore("patient-1");

        assertThat(response.getPatientId()).isEqualTo("patient-1");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getGender()).isEqualTo("F");
    }

    @Test
    void fetchAllergies_mapsRepositoryRows() {
        PatientAllergy allergy = new PatientAllergy();
        allergy.setAllergyId("allergy-1");
        allergy.setAllergen("Penicillin");
        allergy.setSeverity("severe");
        when(patientAllergyRepository.findByPatient_PatientIdAndDeletedAtIsNull("patient-1"))
                .thenReturn(List.of(allergy));

        List<PatientAllergyResponse> allergies = patientService.fetchAllergies("patient-1");

        assertThat(allergies).hasSize(1);
        assertThat(allergies.get(0).getAllergen()).isEqualTo("Penicillin");
    }

    @Test
    void fetchPatientCore_throwsNotFound_whenAbsent() {
        when(patientRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.fetchPatientCore("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void fetchPatientCore_throwsNotFound_whenSoftDeleted() {
        existingPatient.setDeletedAt(LocalDateTime.now());
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));

        assertThatThrownBy(() -> patientService.fetchPatientCore("patient-1"))
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
