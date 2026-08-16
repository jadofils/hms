package amalitech.hospital.management.exception.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServerExceptionTest {

    @Test
    void constructor_setsMessage() {
        InternalServerException ex = new InternalServerException("something broke");
        assertThat(ex.getMessage()).isEqualTo("something broke");
        assertThat(ex).isInstanceOf(ApplicationException.class);
    }
}
