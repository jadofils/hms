package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.pharmacy.PrescriptionRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionResponse;
import amalitech.hospital.management.event.PrescriptionCreatedEvent;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.model.pharmacy.Prescription;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.pharmacy.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private amalitech.hospital.management.repository.pharmacy.PrescriptionItemRepository prescriptionItemRepository;
    @Mock private EventBus eventBus;

    private PrescriptionService prescriptionService;

    private Appointment existingAppointment;
    private Prescription existingPrescription;

    @BeforeEach
    void setUp() {
        prescriptionService = new PrescriptionService(prescriptionRepository, appointmentRepository,
                prescriptionItemRepository, eventBus);

        Patient patient = new Patient();
        patient.setPatientId("patient-1");
        patient.setFirstName("Alice");
        patient.setLastName("Doe");

        Doctor doctor = new Doctor();
        doctor.setDoctorId("doctor-1");
        doctor.setFirstName("Greg");
        doctor.setLastName("House");

        existingAppointment = new Appointment();
        existingAppointment.setAppointmentId("appt-1");
        existingAppointment.setPatient(patient);
        existingAppointment.setDoctor(doctor);

        existingPrescription = new Prescription();
        existingPrescription.setPrescriptionId("presc-1");
        existingPrescription.setAppointment(existingAppointment);
        existingPrescription.setDateIssued(LocalDate.now());
    }

    @Test
    void getPrescription_returnsMappedResponse_whenFoundAndActive() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));

        PrescriptionResponse response = prescriptionService.getPrescription("presc-1");

        assertThat(response.getPrescriptionId()).isEqualTo("presc-1");
        assertThat(response.getAppointmentId()).isEqualTo("appt-1");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getDoctorName()).isEqualTo("Greg House");
    }

    @Test
    void getPrescription_eagerLoadsItems_unlikeThePaginatedListing() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        amalitech.hospital.management.model.pharmacy.PrescriptionItem item =
                new amalitech.hospital.management.model.pharmacy.PrescriptionItem();
        item.setItemId("item-1");
        item.setPrescription(existingPrescription);
        amalitech.hospital.management.model.pharmacy.Medication medication =
                new amalitech.hospital.management.model.pharmacy.Medication();
        medication.setMedicationId("med-1");
        medication.setName("Amoxicillin");
        item.setMedication(medication);
        item.setDosage("500mg");
        item.setQuantity(20);
        when(prescriptionItemRepository.findByPrescription_PrescriptionIdAndDeletedAtIsNull("presc-1"))
                .thenReturn(List.of(item));

        PrescriptionResponse response = prescriptionService.getPrescription("presc-1");

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getMedicationName()).isEqualTo("Amoxicillin");
    }

    @Test
    void getPrescription_throwsNotFound_whenAbsent() {
        when(prescriptionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.getPrescription("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPrescription_throwsNotFound_whenSoftDeleted() {
        existingPrescription.setDeletedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));

        assertThatThrownBy(() -> prescriptionService.getPrescription("presc-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createPrescription_throwsNotFound_whenAppointmentAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());
        PrescriptionRequest request = requestFor("missing", null);

        assertThatThrownBy(() -> prescriptionService.createPrescription(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createPrescription_throwsNotFound_whenAppointmentSoftDeleted() {
        existingAppointment.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        PrescriptionRequest request = requestFor("appt-1", null);

        assertThatThrownBy(() -> prescriptionService.createPrescription(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createPrescription_defaultsDateIssuedToToday_whenOmitted() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));
        PrescriptionRequest request = requestFor("appt-1", null);

        PrescriptionResponse response = prescriptionService.createPrescription(request);

        assertThat(response.getDateIssued()).isEqualTo(LocalDate.now());
        verify(eventBus).publish(any(PrescriptionCreatedEvent.class));
    }

    @Test
    void createPrescription_usesProvidedDateIssued() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate date = LocalDate.of(2020, 1, 1);
        PrescriptionRequest request = requestFor("appt-1", date);

        PrescriptionResponse response = prescriptionService.createPrescription(request);

        assertThat(response.getDateIssued()).isEqualTo(date);
    }

    @Test
    void updatePrescription_throwsNotFound_whenAbsent() {
        when(prescriptionRepository.findById("missing")).thenReturn(Optional.empty());
        PrescriptionRequest request = requestFor("appt-1", null);

        assertThatThrownBy(() -> prescriptionService.updatePrescription("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePrescription_throwsNotFound_whenAppointmentAbsent() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());
        PrescriptionRequest request = requestFor("missing", null);

        assertThatThrownBy(() -> prescriptionService.updatePrescription("presc-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletePrescription_setsDeletedAt() {
        when(prescriptionRepository.findById("presc-1")).thenReturn(Optional.of(existingPrescription));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));

        prescriptionService.deletePrescription("presc-1");

        assertThat(existingPrescription.getDeletedAt()).isNotNull();
    }

    @Test
    void deletePrescription_throwsNotFound_whenAbsent() {
        when(prescriptionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.deletePrescription("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static PrescriptionRequest requestFor(String appointmentId, LocalDate dateIssued) {
        PrescriptionRequest request = new PrescriptionRequest();
        request.setAppointmentId(appointmentId);
        request.setDateIssued(dateIssued);
        return request;
    }
}
