package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(Resource.USERS.getDbValue()).isEqualTo("users");
        assertThat(Resource.USERS.getLabel()).isEqualTo("Users");
        assertThat(Resource.DOCTOR_SCHEDULES.getDbValue()).isEqualTo("doctor-schedules");
        assertThat(Resource.DOCTOR_SCHEDULES.getLabel()).isEqualTo("Doctor Schedules");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(Resource.fromDbValue("USERS")).isEqualTo(Resource.USERS);
        assertThat(Resource.fromDbValue("doctor-schedules")).isEqualTo(Resource.DOCTOR_SCHEDULES);
        assertThat(Resource.fromDbValue("Appointments")).isEqualTo(Resource.APPOINTMENTS);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> Resource.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Resource: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(Resource.PATIENTS.toString()).isEqualTo("patients");
    }
}
