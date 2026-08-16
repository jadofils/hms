package amalitech.hospital.management.exception.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ApplicationException} is abstract — exercised via a throwaway concrete subclass. */
class ApplicationExceptionTest {

    private static class TestException extends ApplicationException {
        TestException(String message) { super(message); }
        TestException(String message, Throwable cause) { super(message, cause); }
    }

    @Test
    void messageOnlyConstructor_setsMessage() {
        TestException ex = new TestException("boom");
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor_setsBoth() {
        Throwable cause = new RuntimeException("root cause");
        TestException ex = new TestException("boom", cause);
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
