package amalitech.hospital.management.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenderTest {

    @Test
    void getDbValue_and_getLabel_returnConstructorValues() {
        assertThat(Gender.M.getDbValue()).isEqualTo("M");
        assertThat(Gender.M.getLabel()).isEqualTo("Male");
        assertThat(Gender.F.getDbValue()).isEqualTo("F");
        assertThat(Gender.OTHER.getDbValue()).isEqualTo("Other");
    }

    @Test
    void fromDbValue_isCaseInsensitive() {
        assertThat(Gender.fromDbValue("m")).isEqualTo(Gender.M);
        assertThat(Gender.fromDbValue("F")).isEqualTo(Gender.F);
        assertThat(Gender.fromDbValue("OTHER")).isEqualTo(Gender.OTHER);
    }

    @Test
    void fromDbValue_throwsForUnknownValue() {
        assertThatThrownBy(() -> Gender.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Gender: bogus");
    }

    @Test
    void fromLabel_isCaseInsensitive() {
        assertThat(Gender.fromLabel("male")).isEqualTo(Gender.M);
        assertThat(Gender.fromLabel("Female")).isEqualTo(Gender.F);
        assertThat(Gender.fromLabel("OTHER")).isEqualTo(Gender.OTHER);
    }

    @Test
    void fromLabel_throwsForUnknownLabel() {
        assertThatThrownBy(() -> Gender.fromLabel("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Gender label: bogus");
    }

    @Test
    void toString_returnsDbValue() {
        assertThat(Gender.M.toString()).isEqualTo("M");
    }
}
