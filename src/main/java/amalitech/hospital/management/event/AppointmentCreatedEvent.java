package amalitech.hospital.management.event;

import amalitech.hospital.management.model.patient.Appointment;

/**
 * Published by {@code AppointmentService.createAppointment} right after the new
 * {@link Appointment} is saved — see {@code amalitech.hospital.management.aop.EventBus}
 * for how {@code @Subscribe}-annotated listener methods receive this.
 */
public record AppointmentCreatedEvent(Appointment appointment) {
}
