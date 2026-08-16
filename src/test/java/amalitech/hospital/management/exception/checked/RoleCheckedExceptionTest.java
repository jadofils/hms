package amalitech.hospital.management.exception.checked;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCheckedExceptionTest {

    @Test
    void messageOnlyConstructor_setsMessage() {
        RoleCheckedException ex = new RoleCheckedException("role not found");
        assertThat(ex.getMessage()).isEqualTo("role not found");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor_setsBoth() {
        Throwable cause = new RuntimeException("root cause");
        RoleCheckedException ex = new RoleCheckedException("role not found", cause);
        assertThat(ex.getMessage()).isEqualTo("role not found");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
