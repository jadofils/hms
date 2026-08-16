package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.Gender;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Gender#getDbValue()} (e.g. {@code "Other"}), not {@link Enum#name()}
 * (e.g. {@code "OTHER"}) — plain {@code @Enumerated(EnumType.STRING)} would write the Java
 * constant name instead and violate the {@code patients.gender} CHECK constraint the moment
 * a value's name and dbValue diverge in case (as {@code OTHER}/{@code "Other"} do).
 */
@Converter(autoApply = false)
public class GenderConverter implements AttributeConverter<Gender, String> {

    @Override
    public String convertToDatabaseColumn(Gender attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public Gender convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Gender.fromDbValue(dbData);
    }
}
