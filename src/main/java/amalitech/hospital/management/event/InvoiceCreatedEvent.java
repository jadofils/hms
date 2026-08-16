package amalitech.hospital.management.event;

import amalitech.hospital.management.model.finance.Invoice;

/**
 * Published by {@code InvoiceService.createInvoice} right after the new {@link Invoice}
 * is saved — see {@code amalitech.hospital.management.aop.EventBus} for how
 * {@code @Subscribe}-annotated listener methods receive this.
 */
public record InvoiceCreatedEvent(Invoice invoice) {
}
