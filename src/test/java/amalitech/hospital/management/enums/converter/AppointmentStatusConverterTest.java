package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.AppointmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusConverterTest {

    private final AppointmentStatusConverter converter = new AppointmentStatusConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(AppointmentStatus.SCHEDULED)).isEqualTo("scheduled");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("completed")).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
