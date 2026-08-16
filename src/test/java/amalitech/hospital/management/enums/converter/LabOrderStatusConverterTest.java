package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.LabOrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabOrderStatusConverterTest {

    private final LabOrderStatusConverter converter = new LabOrderStatusConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(LabOrderStatus.IN_PROGRESS)).isEqualTo("in_progress");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("completed")).isEqualTo(LabOrderStatus.COMPLETED);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
