package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.PatientStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientStatusConverterTest {

    private final PatientStatusConverter converter = new PatientStatusConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(PatientStatus.ACTIVE)).isEqualTo("active");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("inactive")).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
