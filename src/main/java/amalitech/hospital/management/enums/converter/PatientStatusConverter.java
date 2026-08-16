package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.PatientStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link PatientStatus#getDbValue()} (e.g. {@code "active"}), not
 * {@link Enum#name()} (e.g. {@code "ACTIVE"}) — see {@link GenderConverter} for why plain
 * {@code @Enumerated(EnumType.STRING)} would violate the {@code patients.status} CHECK
 * constraint here.
 */
@Converter(autoApply = false)
public class PatientStatusConverter implements AttributeConverter<PatientStatus, String> {

    @Override
    public String convertToDatabaseColumn(PatientStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public PatientStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PatientStatus.fromDbValue(dbData);
    }
}
