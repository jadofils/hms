package amalitech.hospital.management.exception.checked;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserCheckedExceptionTest {

    @Test
    void messageOnlyConstructor_setsMessage() {
        UserCheckedException ex = new UserCheckedException("user not found");
        assertThat(ex.getMessage()).isEqualTo("user not found");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor_setsBoth() {
        Throwable cause = new RuntimeException("root cause");
        UserCheckedException ex = new UserCheckedException("user not found", cause);
        assertThat(ex.getMessage()).isEqualTo("user not found");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
