package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleDayTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(ScheduleDay.MON.getDbValue()).isEqualTo("Mon");
        assertThat(ScheduleDay.MON.getLabel()).isEqualTo("Monday");
        assertThat(ScheduleDay.SUN.getDbValue()).isEqualTo("Sun");
        assertThat(ScheduleDay.SUN.getLabel()).isEqualTo("Sunday");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(ScheduleDay.fromDbValue("mon")).isEqualTo(ScheduleDay.MON);
        assertThat(ScheduleDay.fromDbValue("FRI")).isEqualTo(ScheduleDay.FRI);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> ScheduleDay.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown ScheduleDay: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(ScheduleDay.MON.toString()).isEqualTo("Mon");
    }
}
