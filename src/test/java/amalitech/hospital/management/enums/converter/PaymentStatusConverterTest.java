package amalitech.hospital.management.enums.converter;

import amalitech.hospital.management.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusConverterTest {

    private final PaymentStatusConverter converter = new PaymentStatusConverter();

    @Test
    void convertToDatabaseColumn_returnsDbValue() {
        assertThat(converter.convertToDatabaseColumn(PaymentStatus.PARTIALLY_PAID)).isEqualTo("partially_paid");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_parsesDbValue() {
        assertThat(converter.convertToEntityAttribute("paid")).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
