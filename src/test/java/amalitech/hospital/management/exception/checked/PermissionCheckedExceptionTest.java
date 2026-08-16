package amalitech.hospital.management.exception.checked;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCheckedExceptionTest {

    @Test
    void messageOnlyConstructor_setsMessage() {
        PermissionCheckedException ex = new PermissionCheckedException("permission denied");
        assertThat(ex.getMessage()).isEqualTo("permission denied");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor_setsBoth() {
        Throwable cause = new RuntimeException("root cause");
        PermissionCheckedException ex = new PermissionCheckedException("permission denied", cause);
        assertThat(ex.getMessage()).isEqualTo("permission denied");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
