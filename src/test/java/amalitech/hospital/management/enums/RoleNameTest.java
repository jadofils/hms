package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleNameTest {

    @Test
    void getDbValue_returnsConstructorValue() {
        assertThat(RoleName.ADMIN.getDbValue()).isEqualTo("Admin");
        assertThat(RoleName.DOCTOR.getDbValue()).isEqualTo("Doctor");
        assertThat(RoleName.RECEPTIONIST.getDbValue()).isEqualTo("Receptionist");
        assertThat(RoleName.ANALYST.getDbValue()).isEqualTo("Analyst");
        assertThat(RoleName.PHARMACIST.getDbValue()).isEqualTo("Pharmacist");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(RoleName.fromDbValue("admin")).isEqualTo(RoleName.ADMIN);
        assertThat(RoleName.fromDbValue("DOCTOR")).isEqualTo(RoleName.DOCTOR);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> RoleName.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown RoleName: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(RoleName.ADMIN.toString()).isEqualTo("Admin");
    }
}
