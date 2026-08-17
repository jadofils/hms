package amalitech.hospital.management.docs;

/**
 * One row of the empirical REST-vs-GraphQL benchmark shown on {@code GET /docs/rest-vs-graphql}
 * — a point-in-time snapshot of {@code docs/performance-report.md}'s real, measured numbers
 * (real HTTP round trips, real PostgreSQL, both styles calling the same service-layer method),
 * not the hand-authored conceptual guidance {@link OperationComparison} covers. Re-run
 * {@code RestVsGraphQlBenchmarkTest} (disabled by default — see its Javadoc) to regenerate both
 * the markdown report and this snapshot; there's no live re-benchmarking on every page load,
 * the same way {@link ApiComparisonCatalog}'s own content is deliberately static rather than
 * introspected.
 */
public record BenchmarkResult(
        String operation,
        double avgRestMs,
        double avgGraphQlMs,
        double p95RestMs,
        double p95GraphQlMs,
        int throughputRestOpsPerSec,
        int throughputGraphQlOpsPerSec,
        String faster) {
}
