package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.PaymentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link PaymentStatus#getDbValue()} (e.g. {@code "partially_paid"}), not
 * {@link Enum#name()} (e.g. {@code "PARTIALLY_PAID"}) — see {@link GenderConverter} for
 * why plain {@code @Enumerated(EnumType.STRING)} would violate the
 * {@code invoices.payment_status} CHECK constraint here.
 */
@Converter(autoApply = false)
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(PaymentStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentStatus.fromDbValue(dbData);
    }
}
