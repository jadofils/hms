package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabOrderStatusTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(LabOrderStatus.ORDERED.getDbValue()).isEqualTo("ordered");
        assertThat(LabOrderStatus.ORDERED.getLabel()).isEqualTo("Ordered");
        assertThat(LabOrderStatus.IN_PROGRESS.getDbValue()).isEqualTo("in_progress");
        assertThat(LabOrderStatus.COMPLETED.getDbValue()).isEqualTo("completed");
        assertThat(LabOrderStatus.CANCELLED.getDbValue()).isEqualTo("cancelled");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(LabOrderStatus.fromDbValue("ORDERED")).isEqualTo(LabOrderStatus.ORDERED);
        assertThat(LabOrderStatus.fromDbValue("In_Progress")).isEqualTo(LabOrderStatus.IN_PROGRESS);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> LabOrderStatus.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown LabOrderStatus: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(LabOrderStatus.ORDERED.toString()).isEqualTo("ordered");
    }
}
