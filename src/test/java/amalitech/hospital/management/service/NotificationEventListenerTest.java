package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.event.AppointmentCreatedEvent;
import amalitech.hospital.management.event.InvoiceCreatedEvent;
import amalitech.hospital.management.event.LabResultRecordedEvent;
import amalitech.hospital.management.event.PrescriptionCreatedEvent;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.finance.Invoice;
import amalitech.hospital.management.model.lab.LabOrder;
import amalitech.hospital.management.model.lab.LabResult;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.patient.Patient;
import amalitech.hospital.management.model.pharmacy.Prescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;

    private NotificationEventListener listener;
    private Patient patient;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);

        patient = new Patient();
        patient.setPatientId("patient-1");

        Doctor doctor = new Doctor();
        doctor.setDoctorId("doctor-1");

        appointment = new Appointment();
        appointment.setAppointmentId("appt-1");
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
    }

    @Test
    void onAppointmentCreated_createsNotificationForThePatient() {
        listener.onAppointmentCreated(new AppointmentCreatedEvent(appointment));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("appointment-created");
        assertThat(captor.getValue().getRecipients()).containsExactly("patient-1");
        assertThat(captor.getValue().getPayload()).contains("appt-1");
    }

    @Test
    void onPrescriptionCreated_createsNotificationForThePatient() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("presc-1");
        prescription.setAppointment(appointment);

        listener.onPrescriptionCreated(new PrescriptionCreatedEvent(prescription));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("prescription-created");
        assertThat(captor.getValue().getRecipients()).containsExactly("patient-1");
        assertThat(captor.getValue().getPayload()).contains("presc-1");
    }

    @Test
    void onLabResultRecorded_createsNotificationForThePatient() {
        LabOrder labOrder = new LabOrder();
        labOrder.setLabOrderId("lab-1");
        labOrder.setAppointment(appointment);

        LabResult labResult = new LabResult();
        labResult.setLabResultId("result-1");
        labResult.setLabOrder(labOrder);

        listener.onLabResultRecorded(new LabResultRecordedEvent(labResult));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("lab-result-recorded");
        assertThat(captor.getValue().getRecipients()).containsExactly("patient-1");
        assertThat(captor.getValue().getPayload()).contains("result-1");
    }

    @Test
    void onInvoiceCreated_createsNotificationForThePatient() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId("inv-1");
        invoice.setPatient(patient);
        invoice.setAppointment(appointment);

        listener.onInvoiceCreated(new InvoiceCreatedEvent(invoice));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("invoice-created");
        assertThat(captor.getValue().getRecipients()).containsExactly("patient-1");
        assertThat(captor.getValue().getPayload()).contains("inv-1");
    }
}
