package amalitech.hospital.management.event;

import amalitech.hospital.management.model.lab.LabResult;

/**
 * Published by {@code LabResultService.createResult} right after the new
 * {@link LabResult} is saved — see {@code amalitech.hospital.management.aop.EventBus}
 * for how {@code @Subscribe}-annotated listener methods receive this.
 */
public record LabResultRecordedEvent(LabResult labResult) {
}
