package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.ScheduleDay;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link ScheduleDay#getDbValue()} (e.g. {@code "Mon"}), not {@link Enum#name()}
 * (e.g. {@code "MON"}) — see {@link GenderConverter} for why plain
 * {@code @Enumerated(EnumType.STRING)} would violate the
 * {@code doctor_schedules.day_of_week} CHECK constraint here.
 */
@Converter(autoApply = false)
public class ScheduleDayConverter implements AttributeConverter<ScheduleDay, String> {

    @Override
    public String convertToDatabaseColumn(ScheduleDay attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public ScheduleDay convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ScheduleDay.fromDbValue(dbData);
    }
}
