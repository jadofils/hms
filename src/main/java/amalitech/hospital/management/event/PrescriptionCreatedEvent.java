package amalitech.hospital.management.event;

import amalitech.hospital.management.model.pharmacy.Prescription;

/**
 * Published by {@code PrescriptionService.createPrescription} right after the new
 * {@link Prescription} is saved — see {@code amalitech.hospital.management.aop.EventBus}
 * for how {@code @Subscribe}-annotated listener methods receive this.
 */
public record PrescriptionCreatedEvent(Prescription prescription) {
}
