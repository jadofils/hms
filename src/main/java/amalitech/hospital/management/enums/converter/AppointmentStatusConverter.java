package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.AppointmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link AppointmentStatus#getDbValue()} (e.g. {@code "scheduled"}), not
 * {@link Enum#name()} (e.g. {@code "SCHEDULED"}) — see {@link GenderConverter} for why
 * plain {@code @Enumerated(EnumType.STRING)} would violate the {@code appointments.status}
 * CHECK constraint here.
 */
@Converter(autoApply = false)
public class AppointmentStatusConverter implements AttributeConverter<AppointmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(AppointmentStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public AppointmentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AppointmentStatus.fromDbValue(dbData);
    }
}
