package amalitech.hospital.management.docs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No mocks needed — {@link ApiComparisonCatalog} has no collaborators, just a static benchmark
 * snapshot, so a plain {@code new} is enough (same reasoning as any other dependency-free POJO
 * test in this codebase).
 */
class ApiComparisonCatalogTest {

    private final ApiComparisonCatalog catalog = new ApiComparisonCatalog();

    @Test
    void benchmarkResults_matchesTheGeneratedPerformanceReportRowCount() {
        List<BenchmarkResult> results = catalog.benchmarkResults();

        // docs/performance-report.md documents 8 operations — see RestVsGraphQlBenchmarkTest.
        assertThat(results).hasSize(8);
        assertThat(results).allSatisfy(row -> {
            assertThat(row.operation()).isNotBlank();
            assertThat(row.avgRestMs()).isPositive();
            assertThat(row.avgGraphQlMs()).isPositive();
            assertThat(row.p95RestMs()).isPositive();
            assertThat(row.p95GraphQlMs()).isPositive();
            assertThat(row.throughputRestOpsPerSec()).isPositive();
            assertThat(row.throughputGraphQlOpsPerSec()).isPositive();
            assertThat(row.faster()).isNotBlank();
        });
    }

    @Test
    void maxAvgLatencyMs_isTheLargestAvgAcrossBothStyles() {
        double max = catalog.maxAvgLatencyMs();

        double expected = catalog.benchmarkResults().stream()
                .flatMapToDouble(r -> java.util.stream.DoubleStream.of(r.avgRestMs(), r.avgGraphQlMs()))
                .max().orElseThrow();
        assertThat(max).isEqualTo(expected);
    }

    @Test
    void restWinCount_countsOperationsWhereRestAveragedFasterOrEqual() {
        long expected = catalog.benchmarkResults().stream()
                .filter(r -> r.avgRestMs() <= r.avgGraphQlMs())
                .count();

        assertThat(catalog.restWinCount()).isEqualTo(expected);
    }

    @Test
    void avgRestAndGraphQlMs_areThePlainAverageAcrossAllOperations() {
        List<BenchmarkResult> results = catalog.benchmarkResults();
        double expectedRest = results.stream().mapToDouble(BenchmarkResult::avgRestMs).average().orElseThrow();
        double expectedGraphQl = results.stream().mapToDouble(BenchmarkResult::avgGraphQlMs).average().orElseThrow();

        assertThat(catalog.avgRestMs()).isEqualTo(expectedRest);
        assertThat(catalog.avgGraphQlMs()).isEqualTo(expectedGraphQl);
    }
}
