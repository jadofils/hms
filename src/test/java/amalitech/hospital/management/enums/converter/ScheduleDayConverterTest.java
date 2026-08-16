package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.ScheduleDay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleDayConverterTest {

    private final ScheduleDayConverter converter = new ScheduleDayConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(ScheduleDay.WED)).isEqualTo("Wed");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("Sat")).isEqualTo(ScheduleDay.SAT);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
