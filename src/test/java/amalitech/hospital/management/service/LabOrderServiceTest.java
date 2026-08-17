package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.lab.LabOrderRequest;
import amalitech.hospital.management.dto.lab.LabOrderResponse;
import amalitech.hospital.management.enums.LabOrderStatus;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.lab.LabOrder;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.model.lab.LabResult;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.repository.lab.LabOrderRepository;
import amalitech.hospital.management.repository.lab.LabResultRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabOrderServiceTest {

    @Mock private LabOrderRepository labOrderRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private LabResultRepository labResultRepository;

    private LabOrderService labOrderService;

    private Appointment existingAppointment;
    private Doctor existingDoctor;
    private LabOrder existingLabOrder;

    @BeforeEach
    void setUp() {
        labOrderService = new LabOrderService(labOrderRepository, appointmentRepository, doctorRepository,
                labResultRepository);

        Patient patient = new Patient();
        patient.setPatientId("patient-1");
        patient.setFirstName("Alice");
        patient.setLastName("Doe");

        existingDoctor = new Doctor();
        existingDoctor.setDoctorId("doctor-1");
        existingDoctor.setFirstName("Greg");
        existingDoctor.setLastName("House");

        existingAppointment = new Appointment();
        existingAppointment.setAppointmentId("appt-1");
        existingAppointment.setPatient(patient);
        existingAppointment.setDoctor(existingDoctor);

        existingLabOrder = new LabOrder();
        existingLabOrder.setLabOrderId("lab-1");
        existingLabOrder.setAppointment(existingAppointment);
        existingLabOrder.setDoctor(existingDoctor);
        existingLabOrder.setTestName("Blood Panel");
        existingLabOrder.setStatus(LabOrderStatus.ORDERED);
    }

    @Test
    void getLabOrder_returnsMappedResponse_whenFoundAndActive() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));

        LabOrderResponse response = labOrderService.getLabOrder("lab-1");

        assertThat(response.getLabOrderId()).isEqualTo("lab-1");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getDoctorName()).isEqualTo("Greg House");
        assertThat(response.getStatus()).isEqualTo("ordered");
        assertThat(response.getResult()).isNull();
    }

    @Test
    void getLabOrder_eagerLoadsResult_whenOneHasBeenRecorded() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        LabResult result = new LabResult();
        result.setLabResultId("result-1");
        result.setLabOrder(existingLabOrder);
        result.setResultValue("Negative");
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(result));

        LabOrderResponse response = labOrderService.getLabOrder("lab-1");

        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult().getResultValue()).isEqualTo("Negative");
    }

    @Test
    void getLabOrder_throwsNotFound_whenAbsent() {
        when(labOrderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labOrderService.getLabOrder("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getLabOrder_throwsNotFound_whenSoftDeleted() {
        existingLabOrder.setDeletedAt(LocalDateTime.now());
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));

        assertThatThrownBy(() -> labOrderService.getLabOrder("lab-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createLabOrder_throwsNotFound_whenAppointmentAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());
        LabOrderRequest request = requestFor("missing", "doctor-1");

        assertThatThrownBy(() -> labOrderService.createLabOrder(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createLabOrder_throwsNotFound_whenDoctorAbsent() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());
        LabOrderRequest request = requestFor("appt-1", "missing");

        assertThatThrownBy(() -> labOrderService.createLabOrder(request))
                .isInstanceOf(NotFoundException.class);
        verify(labOrderRepository, never()).save(any());
    }

    @Test
    void createLabOrder_throwsNotFound_whenAppointmentSoftDeleted() {
        existingAppointment.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");

        assertThatThrownBy(() -> labOrderService.createLabOrder(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createLabOrder_throwsNotFound_whenDoctorSoftDeleted() {
        existingDoctor.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");

        assertThatThrownBy(() -> labOrderService.createLabOrder(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createLabOrder_savesWithDefaultOrderedStatus_whenStatusOmitted() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");

        LabOrderResponse response = labOrderService.createLabOrder(request);

        assertThat(response.getStatus()).isEqualTo("ordered");
    }

    @Test
    void createLabOrder_savesWithDefaultOrderedStatus_whenStatusBlank() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");
        request.setStatus("   ");

        LabOrderResponse response = labOrderService.createLabOrder(request);

        assertThat(response.getStatus()).isEqualTo("ordered");
    }

    @Test
    void createLabOrder_throwsBadRequest_whenStatusInvalid() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");
        request.setStatus("bogus");

        assertThatThrownBy(() -> labOrderService.createLabOrder(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateLabOrder_throwsNotFound_whenAbsent() {
        when(labOrderRepository.findById("missing")).thenReturn(Optional.empty());
        LabOrderRequest request = requestFor("appt-1", "doctor-1");

        assertThatThrownBy(() -> labOrderService.updateLabOrder("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateLabOrder_appliesNewStatus_whenProvided() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        LabOrderRequest request = requestFor("appt-1", "doctor-1");
        request.setStatus("completed");

        LabOrderResponse response = labOrderService.updateLabOrder("lab-1", request);

        assertThat(response.getStatus()).isEqualTo("completed");
    }

    @Test
    void deleteLabOrder_setsDeletedAt() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        labOrderService.deleteLabOrder("lab-1");

        assertThat(existingLabOrder.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteLabOrder_throwsNotFound_whenAbsent() {
        when(labOrderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labOrderService.deleteLabOrder("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static LabOrderRequest requestFor(String appointmentId, String doctorId) {
        LabOrderRequest request = new LabOrderRequest();
        request.setAppointmentId(appointmentId);
        request.setDoctorId(doctorId);
        request.setTestName("Blood Panel");
        return request;
    }
}
