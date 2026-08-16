package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.Subscribe;
import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.event.AppointmentCreatedEvent;
import amalitech.hospital.management.event.InvoiceCreatedEvent;
import amalitech.hospital.management.event.LabResultRecordedEvent;
import amalitech.hospital.management.event.PrescriptionCreatedEvent;
import amalitech.hospital.management.model.finance.Invoice;
import amalitech.hospital.management.model.lab.LabResult;
import amalitech.hospital.management.model.patient.Appointment;
import amalitech.hospital.management.model.pharmacy.Prescription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns each domain-create event into a
 * {@link amalitech.hospital.management.model.notification.Notification} via
 * {@link NotificationService#createNotification} — kept separate from
 * {@code NotificationService} itself, which stays a plain CRUD service with no knowledge
 * of these upstream domains. Each method here is registered with
 * {@link amalitech.hospital.management.aop.EventBus} at startup (see that class for the
 * scan/dispatch mechanism); each can be individually toggled off/on at runtime via
 * {@code EventSubscriptionController}'s subscribe/unsubscribe endpoints, keyed by the
 * matching {@link Subscribe#name()}.
 *
 * Recipients are recorded as the affected {@code Patient}'s id. Neither {@code Patient}
 * nor {@code Doctor} has a linked {@code User}/login account in this schema, so a
 * patient's own id is the closest identifier available for "who this notification is
 * about" — {@code Notification.recipients} isn't validated against real {@code User}
 * ids anywhere (see {@code NotificationService}'s own Javadoc: it's opaque, loosely-typed
 * JSON, not an enforced FK), so this is consistent with how the column is already used.
 */
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Subscribe(name = "notification-on-appointment-created", event = AppointmentCreatedEvent.class)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        Appointment appointment = event.appointment();
        notificationService.createNotification(requestFor("appointment-created",
                appointment.getPatient().getPatientId(),
                "{\"appointmentId\":\"" + appointment.getAppointmentId() + "\"}"));
    }

    @Subscribe(name = "notification-on-prescription-created", event = PrescriptionCreatedEvent.class)
    public void onPrescriptionCreated(PrescriptionCreatedEvent event) {
        Prescription prescription = event.prescription();
        notificationService.createNotification(requestFor("prescription-created",
                prescription.getAppointment().getPatient().getPatientId(),
                "{\"prescriptionId\":\"" + prescription.getPrescriptionId() + "\"}"));
    }

    @Subscribe(name = "notification-on-lab-result-recorded", event = LabResultRecordedEvent.class)
    public void onLabResultRecorded(LabResultRecordedEvent event) {
        LabResult labResult = event.labResult();
        notificationService.createNotification(requestFor("lab-result-recorded",
                labResult.getLabOrder().getAppointment().getPatient().getPatientId(),
                "{\"labResultId\":\"" + labResult.getLabResultId() + "\"}"));
    }

    @Subscribe(name = "notification-on-invoice-created", event = InvoiceCreatedEvent.class)
    public void onInvoiceCreated(InvoiceCreatedEvent event) {
        Invoice invoice = event.invoice();
        notificationService.createNotification(requestFor("invoice-created",
                invoice.getPatient().getPatientId(),
                "{\"invoiceId\":\"" + invoice.getInvoiceId() + "\"}"));
    }

    private NotificationRequest requestFor(String type, String recipientPatientId, String payload) {
        NotificationRequest request = new NotificationRequest();
        request.setType(type);
        request.setRecipients(List.of(recipientPatientId));
        request.setPayload(payload);
        return request;
    }
}
