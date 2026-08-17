package amalitech.hospital.management.docs;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The static content behind {@code GET /docs/rest-vs-graphql} — a point-in-time snapshot of
 * {@code docs/performance-report.md}'s real {@code RestVsGraphQlBenchmarkTest} results (real
 * HTTP round trips, real PostgreSQL data, both styles calling the same service-layer method per
 * operation). Deliberately empirical only, not hand-authored conceptual guidance: re-run the
 * benchmark test (disabled by default — see its Javadoc) to regenerate both the markdown report
 * and this snapshot rather than editing these numbers by hand.
 */
@Component
public class ApiComparisonCatalog {

    public static final int ITERATIONS_PER_OPERATION = 30;

    public List<BenchmarkResult> benchmarkResults() {
        return List.of(
                new BenchmarkResult("Get Doctor by id", 24.55, 40.23, 105.73, 46.77, 41, 25, "REST (+39.0%)"),
                new BenchmarkResult("List Doctors (page=0,size=20)", 17.63, 31.55, 18.31, 44.66, 57, 32, "REST (+44.1%)"),
                new BenchmarkResult("Get Patient by id", 16.89, 19.21, 61.15, 39.85, 59, 52, "REST (+12.1%)"),
                new BenchmarkResult("Get Role by id", 13.96, 103.66, 18.28, 134.43, 72, 10, "REST (+86.5%)"),
                new BenchmarkResult("Get Appointment by id", 11.41, 18.45, 15.53, 36.20, 88, 54, "REST (+38.2%)"),
                new BenchmarkResult("Create Doctor", 31.58, 31.90, 39.63, 40.32, 32, 31, "REST (+1.0%)"),
                new BenchmarkResult("Update Doctor", 14.80, 27.27, 17.11, 32.85, 68, 37, "REST (+45.7%)"),
                new BenchmarkResult("Delete Doctor", 11.64, 17.40, 14.73, 29.81, 86, 57, "REST (+33.1%)"));
    }

    /** Shared scale for both latency charts on the page — so a REST bar and a GraphQL bar of
     *  the same pixel height genuinely represent the same latency, not two independently
     *  auto-scaled axes that could visually mislead. */
    public double maxAvgLatencyMs() {
        return benchmarkResults().stream()
                .flatMapToDouble(row -> java.util.stream.DoubleStream.of(row.avgRestMs(), row.avgGraphQlMs()))
                .max()
                .orElse(1.0);
    }

    public long restWinCount() {
        return benchmarkResults().stream().filter(r -> r.avgRestMs() <= r.avgGraphQlMs()).count();
    }

    public double avgRestMs() {
        return benchmarkResults().stream().mapToDouble(BenchmarkResult::avgRestMs).average().orElse(0);
    }

    public double avgGraphQlMs() {
        return benchmarkResults().stream().mapToDouble(BenchmarkResult::avgGraphQlMs).average().orElse(0);
    }
}
