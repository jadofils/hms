package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.Gender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenderConverterTest {

    private final GenderConverter converter = new GenderConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(Gender.OTHER)).isEqualTo("Other");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("F")).isEqualTo(Gender.F);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
