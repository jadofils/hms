package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.finance.InvoiceRequest;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.enums.PaymentStatus;
import amalitech.hospital.management.event.InvoiceCreatedEvent;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.finance.Invoice;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.repository.finance.InvoiceRepository;
import amalitech.hospital.management.repository.patient.AppointmentRepository;
import amalitech.hospital.management.repository.patient.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private EventBus eventBus;

    private InvoiceService invoiceService;

    private Appointment existingAppointment;
    private Patient existingPatient;
    private Invoice existingInvoice;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, appointmentRepository, patientRepository, eventBus);

        existingPatient = new Patient();
        existingPatient.setPatientId("patient-1");
        existingPatient.setFirstName("Alice");
        existingPatient.setLastName("Doe");

        existingAppointment = new Appointment();
        existingAppointment.setAppointmentId("appt-1");
        existingAppointment.setPatient(existingPatient);

        existingInvoice = new Invoice();
        existingInvoice.setInvoiceId("inv-1");
        existingInvoice.setAppointment(existingAppointment);
        existingInvoice.setPatient(existingPatient);
        existingInvoice.setTotalAmount(new BigDecimal("100.00"));
        existingInvoice.setPaymentStatus(PaymentStatus.UNPAID);
    }

    @Test
    void getInvoice_returnsMappedResponse_whenFoundAndActive() {
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(existingInvoice));

        InvoiceResponse response = invoiceService.getInvoice("inv-1");

        assertThat(response.getInvoiceId()).isEqualTo("inv-1");
        assertThat(response.getPatientName()).isEqualTo("Alice Doe");
        assertThat(response.getPaymentStatus()).isEqualTo("unpaid");
    }

    @Test
    void getInvoice_throwsNotFound_whenAbsent() {
        when(invoiceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoice("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getInvoice_throwsNotFound_whenSoftDeleted() {
        existingInvoice.setDeletedAt(LocalDateTime.now());
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(existingInvoice));

        assertThatThrownBy(() -> invoiceService.getInvoice("inv-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvoice_throwsNotFound_whenAppointmentAbsent() {
        when(appointmentRepository.findById("missing")).thenReturn(Optional.empty());
        InvoiceRequest request = requestFor("missing", "patient-1");

        assertThatThrownBy(() -> invoiceService.createInvoice(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvoice_throwsNotFound_whenPatientAbsent() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("missing")).thenReturn(Optional.empty());
        InvoiceRequest request = requestFor("appt-1", "missing");

        assertThatThrownBy(() -> invoiceService.createInvoice(request))
                .isInstanceOf(NotFoundException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void createInvoice_throwsNotFound_whenAppointmentSoftDeleted() {
        existingAppointment.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        InvoiceRequest request = requestFor("appt-1", "patient-1");

        assertThatThrownBy(() -> invoiceService.createInvoice(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvoice_throwsNotFound_whenPatientSoftDeleted() {
        existingPatient.setDeletedAt(LocalDateTime.now());
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        InvoiceRequest request = requestFor("appt-1", "patient-1");

        assertThatThrownBy(() -> invoiceService.createInvoice(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvoice_appliesDefaultStatus_whenPaymentStatusBlank() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        InvoiceRequest request = requestFor("appt-1", "patient-1");
        request.setPaymentStatus("   ");

        InvoiceResponse response = invoiceService.createInvoice(request);

        assertThat(response.getPaymentStatus()).isEqualTo("unpaid");
    }

    @Test
    void createInvoice_appliesDefaults_whenAmountAndStatusOmitted() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        InvoiceRequest request = requestFor("appt-1", "patient-1");

        InvoiceResponse response = invoiceService.createInvoice(request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPaymentStatus()).isEqualTo("unpaid");
        verify(eventBus).publish(any(InvoiceCreatedEvent.class));
    }

    @Test
    void createInvoice_throwsBadRequest_whenPaymentStatusInvalid() {
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        InvoiceRequest request = requestFor("appt-1", "patient-1");
        request.setPaymentStatus("bogus");

        assertThatThrownBy(() -> invoiceService.createInvoice(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateInvoice_throwsNotFound_whenAbsent() {
        when(invoiceRepository.findById("missing")).thenReturn(Optional.empty());
        InvoiceRequest request = requestFor("appt-1", "patient-1");

        assertThatThrownBy(() -> invoiceService.updateInvoice("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateInvoice_appliesNewPaymentStatus_whenProvided() {
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(existingInvoice));
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(existingAppointment));
        when(patientRepository.findById("patient-1")).thenReturn(Optional.of(existingPatient));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        InvoiceRequest request = requestFor("appt-1", "patient-1");
        request.setPaymentStatus("paid");

        InvoiceResponse response = invoiceService.updateInvoice("inv-1", request);

        assertThat(response.getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void deleteInvoice_setsDeletedAt() {
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(existingInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.deleteInvoice("inv-1");

        assertThat(existingInvoice.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteInvoice_throwsNotFound_whenAbsent() {
        when(invoiceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.deleteInvoice("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static InvoiceRequest requestFor(String appointmentId, String patientId) {
        InvoiceRequest request = new InvoiceRequest();
        request.setAppointmentId(appointmentId);
        request.setPatientId(patientId);
        return request;
    }
}
