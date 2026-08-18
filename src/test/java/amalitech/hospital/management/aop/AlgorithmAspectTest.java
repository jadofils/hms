package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.ApplyAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link AlgorithmAspect} through a real Spring AOP proxy. The mergeSort
 * branch already runs for real via {@code RoleService.getRolePermissions} (see
 * {@code RoleControllerTest}'s permission-grant flow), and binarySearch now runs for
 * real too via {@code AppointmentService}'s double-booking guard (see
 * {@code AppointmentServiceTest}'s conflict tests and
 * {@code AppointmentControllerTest}'s end-to-end coverage) — this test-local bean still
 * exists to exercise the aspect itself directly (binarySearch's own branch, plus the
 * fallback that just runs the original method when the args don't match either
 * algorithm's expected shape), isolated from any one caller's own business logic.
 */
@SpringBootTest
@ActiveProfiles("test")
class AlgorithmAspectTest {

    @Autowired
    private TestAlgorithmBean bean;

    @Test
    void binarySearch_findsIndexOfKey_onSortedList() {
        List<String> sorted = new ArrayList<>(List.of("a", "b", "c", "d"));
        int index = bean.search(sorted, "c", Function.identity());
        assertThat(index).isEqualTo(2);
    }

    @Test
    void binarySearch_returnsMinusOne_whenKeyNotPresent() {
        List<String> sorted = new ArrayList<>(List.of("a", "b", "d"));
        int index = bean.search(sorted, "c", Function.identity());
        assertThat(index).isEqualTo(-1);
    }

    @Test
    void unmatchedAlgorithmValue_fallsThroughToOriginalMethodBody() {
        // Neither "mergeSort" nor "binarySearch" — AlgorithmAspect just proceeds.
        List<String> result = bean.passthrough(List.of("x", "y"));
        assertThat(result).containsExactly("x", "y");
    }

    @TestConfiguration
    static class Config {
        @Bean
        TestAlgorithmBean testAlgorithmBean() {
            return new TestAlgorithmBean();
        }
    }

    /** Test-only stand-in for a real caller — see class Javadoc. */
    public static class TestAlgorithmBean {
        @ApplyAlgorithm("binarySearch")
        public <T> int search(List<T> list, Object targetKey, Function<T, ?> keyExtractor) {
            throw new IllegalStateException("AlgorithmAspect did not intercept this call");
        }

        @ApplyAlgorithm("noSuchAlgorithm")
        public <T> List<T> passthrough(List<T> list) {
            return list;
        }
    }
}
