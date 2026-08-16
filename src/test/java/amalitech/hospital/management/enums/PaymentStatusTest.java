package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(PaymentStatus.UNPAID.getDbValue()).isEqualTo("unpaid");
        assertThat(PaymentStatus.UNPAID.getLabel()).isEqualTo("Unpaid");
        assertThat(PaymentStatus.PARTIALLY_PAID.getDbValue()).isEqualTo("partially_paid");
        assertThat(PaymentStatus.PAID.getDbValue()).isEqualTo("paid");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(PaymentStatus.fromDbValue("UNPAID")).isEqualTo(PaymentStatus.UNPAID);
        assertThat(PaymentStatus.fromDbValue("Partially_Paid")).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> PaymentStatus.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown PaymentStatus: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(PaymentStatus.UNPAID.toString()).isEqualTo("unpaid");
    }
}
