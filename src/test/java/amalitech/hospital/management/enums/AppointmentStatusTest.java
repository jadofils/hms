package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStatusTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(AppointmentStatus.SCHEDULED.getDbValue()).isEqualTo("scheduled");
        assertThat(AppointmentStatus.SCHEDULED.getLabel()).isEqualTo("Scheduled");
        assertThat(AppointmentStatus.COMPLETED.getDbValue()).isEqualTo("completed");
        assertThat(AppointmentStatus.CANCELLED.getDbValue()).isEqualTo("cancelled");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(AppointmentStatus.fromDbValue("SCHEDULED")).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(AppointmentStatus.fromDbValue("Completed")).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(AppointmentStatus.fromDbValue("cancelled")).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> AppointmentStatus.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown AppointmentStatus: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(AppointmentStatus.SCHEDULED.toString()).isEqualTo("scheduled");
    }
}
