package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionActionTest {

    @Test
    void getDbValue_returnsConstructorValue() {
        assertThat(PermissionAction.CREATE.getDbValue()).isEqualTo("create");
        assertThat(PermissionAction.READ.getDbValue()).isEqualTo("read");
        assertThat(PermissionAction.UPDATE.getDbValue()).isEqualTo("update");
        assertThat(PermissionAction.DELETE.getDbValue()).isEqualTo("delete");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(PermissionAction.fromDbValue("CREATE")).isEqualTo(PermissionAction.CREATE);
        assertThat(PermissionAction.fromDbValue("Read")).isEqualTo(PermissionAction.READ);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> PermissionAction.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown PermissionAction: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(PermissionAction.CREATE.toString()).isEqualTo("create");
    }
}
