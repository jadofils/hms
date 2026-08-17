package amalitech.hospital.management.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The empirical "REST vs GraphQL analysis" performance report the project's own
 * {@code ReadMe.md} Deliverables table names, and {@code docs/story-2.2-receptionist-filtering.md}
 * explicitly deferred until GraphQL (Epic 4) existed to compare against. Both styles hit the
 * <em>same</em> service layer over the <em>same</em> Postgres database (see CLAUDE.md's "one
 * service layer, multiple front doors" principle) — this measures REST's HTTP+DTO-mapping
 * overhead against GraphQL's HTTP+query-parsing+field-resolution overhead, not indexing or
 * database tuning (that's a separate, already-covered concern — see {@code FindUserDataAspect}).
 *
 * <p>Deliberately excluded from the regular suite ({@code @Disabled}): this is slow (each of
 * the 8 operations below runs {@link #ITERATIONS} real HTTP round trips per style — 480 requests
 * total), it creates real rows in the shared dev/test Postgres database (consistent with this
 * project's existing test convention — see {@code AbstractControllerTest}'s own Javadoc), and it
 * measures wall-clock timing rather than correctness, so a slow CI machine or background load
 * would make it flaky as a pass/fail gate. Re-enable and run explicitly to regenerate
 * {@code docs/performance-report.md}:
 * <pre>{@code ./mvnw test -Dtest=RestVsGraphQlBenchmarkTest}</pre>
 * (after commenting out the {@code @Disabled} line below).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled("Manual benchmark for docs/performance-report.md — see class Javadoc")
class RestVsGraphQlBenchmarkTest {

    private static final int ITERATIONS = 30;

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl;
    private String token;

    // Same collision-proof approach as AbstractControllerTest.uniqueDigits (pure integer
    // addition, not string concatenation — see that class's Javadoc for why the naive
    // nanoTime-string-concat approach could actually collide).
    private static final long RUN_OFFSET = System.nanoTime();
    private static final AtomicLong SEQ = new AtomicLong();

    private static String uniqueDigits(int len) {
        long value = RUN_OFFSET + SEQ.incrementAndGet();
        String digits = Long.toString(Math.abs(value));
        if (digits.length() < len) {
            digits = "0".repeat(len - digits.length()) + digits;
        }
        return digits.substring(digits.length() - len);
    }

    @Test
    void runFullBenchmarkAndWriteReport() throws Exception {
        baseUrl = "http://localhost:" + port;
        token = login();

        List<BenchmarkRow> rows = new ArrayList<>();

        // ── Fixtures shared by every read-benchmark below ──────────────────────
        String departmentId = createDepartment();
        String doctorId = createDoctor(departmentId);
        String patientId = createPatient();
        String roleId = firstSeededRoleId();
        String appointmentId = createAppointment(patientId, doctorId);

        // ── 1. Get a single doctor by id (eager-loaded departments both ways) ──
        rows.add(benchmark("Get Doctor by id",
                () -> restGet("/api/v1/doctors/" + doctorId),
                () -> graphQl("{ doctor(doctorId: \"" + doctorId + "\") { doctorId firstName "
                        + "lastName specialization phone email departments { departmentId name } } }")));

        // ── 2. List doctors, paginated ──────────────────────────────────────────
        rows.add(benchmark("List Doctors (page=0,size=20)",
                () -> restGet("/api/v1/doctors?page=0&size=20"),
                () -> graphQl("{ doctors(page: 0, size: 20) { doctorId firstName lastName "
                        + "specialization phone email } }")));

        // ── 3. Get a single patient by id (now eager-loads 8 related lists) ────
        rows.add(benchmark("Get Patient by id",
                () -> restGet("/api/v1/patients/" + patientId),
                () -> graphQl("{ patient(patientId: \"" + patientId + "\") { patientId firstName "
                        + "lastName dob gender phone email address status } }")));

        // ── 4. Get a single role by id (eager-loaded permissions both ways) ────
        rows.add(benchmark("Get Role by id",
                () -> restGet("/api/v1/roles/" + roleId),
                () -> graphQl("{ role(roleId: \"" + roleId + "\") { roleId roleName description "
                        + "permissions { permissionId resource action } } }")));

        // ── 5. Get a single appointment by id (nested patient+doctor both ways) ─
        rows.add(benchmark("Get Appointment by id",
                () -> restGet("/api/v1/appointments/" + appointmentId),
                () -> graphQl("{ appointment(appointmentId: \"" + appointmentId + "\") { "
                        + "appointmentId patientName doctorName appointmentDate status reason "
                        + "patient { patientId firstName lastName } doctor { doctorId firstName lastName } } }")));

        // ── 6. Create a doctor ──────────────────────────────────────────────────
        rows.add(benchmark("Create Doctor",
                () -> restPost("/api/v1/doctors", newDoctorBody(departmentId)),
                () -> graphQl(newDoctorMutation(departmentId))));

        // ── 7. Update a doctor (one dedicated target per style, updated repeatedly) ─
        String restUpdateTarget = createDoctor(departmentId);
        String gqlUpdateTarget = createDoctor(departmentId);
        rows.add(benchmark("Update Doctor",
                () -> restPut("/api/v1/doctors/" + restUpdateTarget, updateDoctorBody()),
                () -> graphQl(updateDoctorMutation(gqlUpdateTarget))));

        // ── 8. Delete a doctor (disposable targets pre-created, one per iteration) ─
        List<String> restDeleteTargets = createDoctors(ITERATIONS, departmentId);
        List<String> gqlDeleteTargets = createDoctors(ITERATIONS, departmentId);
        rows.add(benchmarkDelete("Delete Doctor", restDeleteTargets, gqlDeleteTargets));

        String report = renderReport(rows);
        System.out.println(report);
        Path reportPath = Path.of("docs", "performance-report.md");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        assertThat(reportPath).exists();
    }

    // ── Measurement ──────────────────────────────────────────────────────────

    private interface Call {
        void run() throws Exception;
    }

    private BenchmarkRow benchmark(String operation, Call restCall, Call graphQlCall) throws Exception {
        return new BenchmarkRow(operation, measure(restCall), measure(graphQlCall));
    }

    /** Delete needs a distinct target per iteration (can't delete the same row twice), so it
     *  gets its own timing loop instead of reusing {@link #measure}. */
    private BenchmarkRow benchmarkDelete(String operation, List<String> restTargets, List<String> gqlTargets) {
        long[] restTimings = new long[ITERATIONS];
        long restStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            restTemplate.exchange(baseUrl + "/api/v1/doctors/" + restTargets.get(i), HttpMethod.DELETE,
                    authEntity(null), Void.class);
            restTimings[i] = System.nanoTime() - t0;
        }
        Stats restStats = Stats.of(restTimings, System.nanoTime() - restStart);

        long[] gqlTimings = new long[ITERATIONS];
        long gqlStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            graphQl("mutation { deleteDoctor(doctorId: \"" + gqlTargets.get(i) + "\") }");
            gqlTimings[i] = System.nanoTime() - t0;
        }
        Stats gqlStats = Stats.of(gqlTimings, System.nanoTime() - gqlStart);
        return new BenchmarkRow(operation, restStats, gqlStats);
    }

    private Stats measure(Call call) throws Exception {
        long[] timings = new long[ITERATIONS];
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            call.run();
            timings[i] = System.nanoTime() - t0;
        }
        return Stats.of(timings, System.nanoTime() - start);
    }

    private record Stats(double avgMs, double p95Ms, double throughputOpsPerSec) {
        static Stats of(long[] timingsNs, long totalNs) {
            long[] sorted = timingsNs.clone();
            Arrays.sort(sorted);
            double sum = 0;
            for (long t : timingsNs) sum += t;
            double avgMs = (sum / timingsNs.length) / 1_000_000.0;
            int p95Index = Math.min(sorted.length - 1, (int) Math.ceil(0.95 * sorted.length) - 1);
            double p95Ms = sorted[Math.max(0, p95Index)] / 1_000_000.0;
            double seconds = totalNs / 1_000_000_000.0;
            double throughput = timingsNs.length / seconds;
            return new Stats(avgMs, p95Ms, throughput);
        }
    }

    private record BenchmarkRow(String operation, Stats rest, Stats graphQl) {
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private HttpEntity<String> authEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> restGet(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, authEntity(null), String.class);
    }

    private ResponseEntity<String> restPost(String path, String body) {
        return restTemplate.postForEntity(baseUrl + path, authEntity(body), String.class);
    }

    private ResponseEntity<String> restPut(String path, String body) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.PUT, authEntity(body), String.class);
    }

    private ResponseEntity<String> graphQl(String query) {
        String body = "{\"query\":\"" + query.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return restTemplate.postForEntity(baseUrl + "/graphql", authEntity(body), String.class);
    }

    // ── Fixtures (unmeasured setup — plain REST calls, same as AbstractControllerTest) ──

    private String login() {
        String body = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login", new HttpEntity<>(body, headers), String.class);
        return readField(response.getBody(), "/data/token");
    }

    private String createDepartment() {
        String body = "{\"name\":\"Bench Dept " + uniqueDigits(6) + "\",\"location\":\"Main Building\"}";
        ResponseEntity<String> response = restPost("/api/v1/departments", body);
        return readField(response.getBody(), "/data/departmentId");
    }

    private String createDoctor(String departmentId) {
        String body = "{\"firstName\":\"Bench\",\"lastName\":\"Doctor\",\"specialization\":\"General\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"benchdoc" + uniqueDigits(6)
                + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
        ResponseEntity<String> response = restPost("/api/v1/doctors", body);
        return readField(response.getBody(), "/data/doctorId");
    }

    private List<String> createDoctors(int count, String departmentId) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(createDoctor(departmentId));
        }
        return ids;
    }

    private String newDoctorBody(String departmentId) {
        return "{\"firstName\":\"Bench\",\"lastName\":\"Created\",\"specialization\":\"General\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"benchcreate" + uniqueDigits(6)
                + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
    }

    private String newDoctorMutation(String departmentId) {
        return "mutation { createDoctor(input: { firstName: \"Bench\", lastName: \"Created\", "
                + "specialization: \"General\", phone: \"" + uniqueDigits(9) + "\", "
                + "email: \"benchcreate" + uniqueDigits(6) + "@example.com\", "
                + "departmentIds: [\"" + departmentId + "\"] }) { doctorId } }";
    }

    // lastName is letters/spaces/hyphens/apostrophes only (no digits) — updates don't need a
    // unique value here anyway, unlike email/phone, which carry real uniqueness constraints.
    private String updateDoctorBody() {
        return "{\"firstName\":\"Bench\",\"lastName\":\"Updated\",\"specialization\":\"Cardiology\"}";
    }

    private String updateDoctorMutation(String doctorId) {
        return "mutation { updateDoctor(doctorId: \"" + doctorId + "\", input: { firstName: \"Bench\", "
                + "lastName: \"Updated\", specialization: \"Cardiology\" }) { doctorId } }";
    }

    private String createPatient() {
        String body = "{\"firstName\":\"Bench\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"M\",\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"benchpat"
                + uniqueDigits(6) + "@example.com\",\"address\":\"123 Main St\"}";
        ResponseEntity<String> response = restPost("/api/v1/patients", body);
        return readField(response.getBody(), "/data/patientId");
    }

    private String createAppointment(String patientId, String doctorId) {
        String body = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId
                + "\",\"appointmentDate\":\"2099-01-01T10:00:00\",\"reason\":\"Benchmark checkup\"}";
        ResponseEntity<String> response = restPost("/api/v1/appointments", body);
        return readField(response.getBody(), "/data/appointmentId");
    }

    private String firstSeededRoleId() {
        ResponseEntity<String> response = restGet("/api/v1/roles?page=0&size=1");
        return readField(response.getBody(), "/data/content/0/roleId");
    }

    private String readField(String json, String pointer) {
        try {
            JsonNode node = objectMapper.readTree(json).at(pointer);
            return node.asText();
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse response: " + json, e);
        }
    }

    // ── Report rendering ─────────────────────────────────────────────────────

    private String renderReport(List<BenchmarkRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Performance Report — REST vs GraphQL\n\n");
        sb.append("The `ReadMe.md` Deliverables table names this \"REST vs GraphQL analysis\"; ")
                .append("`docs/story-2.2-receptionist-filtering.md` deferred it until GraphQL (Epic 4) ")
                .append("existed to compare against. Both styles call the *same* service layer against ")
                .append("the *same* PostgreSQL database — this isolates REST's HTTP+DTO-mapping overhead ")
                .append("from GraphQL's HTTP+query-parsing+field-resolution overhead. It is not an ")
                .append("indexing/database-tuning study (see `FindUserDataAspect`/Section 5.7-equivalent ")
                .append("work for that).\n\n");
        sb.append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("  \n**Iterations per operation per style:** ").append(ITERATIONS).append("  \n")
                .append("**Environment:** single development machine, real PostgreSQL, real HTTP round trips ")
                .append("via a random-port embedded Tomcat (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).\n\n");
        sb.append("## Results\n\n");
        sb.append("| Operation | Avg REST (ms) | Avg GraphQL (ms) | P95 REST (ms) | P95 GraphQL (ms) | ")
                .append("Tput REST (ops/s) | Tput GraphQL (ops/s) | Faster |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (BenchmarkRow row : rows) {
            String faster = row.rest.avgMs() <= row.graphQl.avgMs()
                    ? String.format("REST (+%.1f%%)", pctFaster(row.graphQl.avgMs(), row.rest.avgMs()))
                    : String.format("GraphQL (+%.1f%%)", pctFaster(row.rest.avgMs(), row.graphQl.avgMs()));
            sb.append(String.format("| %s | %.2f | %.2f | %.2f | %.2f | %.0f | %.0f | %s |%n",
                    row.operation, row.rest.avgMs(), row.graphQl.avgMs(), row.rest.p95Ms(), row.graphQl.p95Ms(),
                    row.rest.throughputOpsPerSec(), row.graphQl.throughputOpsPerSec(), faster));
        }
        sb.append("\n").append(renderAnalysis(rows));
        sb.append("\n*Generated by `RestVsGraphQlBenchmarkTest.runFullBenchmarkAndWriteReport` — re-run it ")
                .append("(after commenting out its `@Disabled`) to regenerate this table.*\n");
        return sb.toString();
    }

    /** Computed from the actual results every time this regenerates — never hand-edited prose
     *  that could silently drift out of sync with a re-run's real numbers. */
    private String renderAnalysis(List<BenchmarkRow> rows) {
        long restWins = rows.stream().filter(r -> r.rest.avgMs() <= r.graphQl.avgMs()).count();
        double avgRestMs = rows.stream().mapToDouble(r -> r.rest.avgMs()).average().orElse(0);
        double avgGraphQlMs = rows.stream().mapToDouble(r -> r.graphQl.avgMs()).average().orElse(0);
        BenchmarkRow biggestGap = rows.stream()
                .max((a, b) -> Double.compare(
                        Math.abs(a.rest.avgMs() - a.graphQl.avgMs()) / Math.max(a.rest.avgMs(), a.graphQl.avgMs()),
                        Math.abs(b.rest.avgMs() - b.graphQl.avgMs()) / Math.max(b.rest.avgMs(), b.graphQl.avgMs())))
                .orElseThrow();
        String biggestGapWinner = biggestGap.rest.avgMs() <= biggestGap.graphQl.avgMs() ? "REST" : "GraphQL";

        StringBuilder sb = new StringBuilder("## Analysis\n\n");
        sb.append(String.format("- REST was faster (lower avg latency) in %d of %d operations measured.%n",
                restWins, rows.size()));
        sb.append(String.format("- Across all operations, REST averaged %.2f ms and GraphQL averaged %.2f ms per "
                + "request.%n", avgRestMs, avgGraphQlMs));
        sb.append(String.format("- The largest relative gap was on **%s**, where %s was faster.%n",
                biggestGap.operation, biggestGapWinner));
        sb.append("- Both styles call the exact same service-layer method for a given operation (e.g. both "
                + "`GET /api/v1/roles/{id}` and `query { role(...) }` call `RoleService.getRole`), so the gap "
                + "measured here is transport/protocol overhead — GraphQL's per-request query parsing, "
                + "validation, and field-by-field resolution — not database or business-logic cost.\n");
        sb.append("- **Conclusion:** for the fixed, well-known request shapes this project's own frontend "
                + "sends today, REST's simpler request/response cycle has a real, measurable latency edge over "
                + "GraphQL on the same data. GraphQL's own advantage — letting a caller request exactly the "
                + "fields/nesting depth it needs in one round trip — matters more for callers with "
                + "heterogeneous or deeply-nested field needs (see the live decision reference at "
                + "`GET /docs/rest-vs-graphql`) than for raw per-request speed on a single fixed shape.\n");
        return sb.toString();
    }

    private double pctFaster(double slowerMs, double fasterMs) {
        return ((slowerMs - fasterMs) / slowerMs) * 100.0;
    }
}
