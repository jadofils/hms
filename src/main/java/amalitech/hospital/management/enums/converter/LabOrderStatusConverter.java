package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.LabOrderStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link LabOrderStatus#getDbValue()} (e.g. {@code "in_progress"}), not
 * {@link Enum#name()} (e.g. {@code "IN_PROGRESS"}) — see {@link GenderConverter} for why
 * plain {@code @Enumerated(EnumType.STRING)} would violate the {@code lab_orders.status}
 * CHECK constraint here.
 */
@Converter(autoApply = false)
public class LabOrderStatusConverter implements AttributeConverter<LabOrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(LabOrderStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public LabOrderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LabOrderStatus.fromDbValue(dbData);
    }
}
