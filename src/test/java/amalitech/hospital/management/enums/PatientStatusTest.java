package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientStatusTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(PatientStatus.ACTIVE.getDbValue()).isEqualTo("active");
        assertThat(PatientStatus.ACTIVE.getLabel()).isEqualTo("Active");
        assertThat(PatientStatus.INACTIVE.getDbValue()).isEqualTo("inactive");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(PatientStatus.fromDbValue("ACTIVE")).isEqualTo(PatientStatus.ACTIVE);
        assertThat(PatientStatus.fromDbValue("Inactive")).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> PatientStatus.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown PatientStatus: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(PatientStatus.ACTIVE.toString()).isEqualTo("active");
    }
}
