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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the actual speedup {@code @Cacheable} buys on {@code GET /api/v1/doctors/{id}}
 * (backed by {@code DoctorService.getDoctor}) — the "Performance improvements measured"
 * acceptance criterion HMS v2's Epic 4 ("Caching and Performance Enhancement") asks for,
 * done the same honest, real-measurement way {@code RestVsGraphQlBenchmarkTest} covers
 * REST vs GraphQL: real HTTP round trips against a real Redis instance and a real
 * Postgres row, not a synthetic estimate.
 *
 * <p>Goes through real HTTP (not a direct service-method call) for the same reason the
 * REST-vs-GraphQL benchmark does: it's how caching actually gets exercised in
 * production, and it sidesteps a real subtlety a direct call would hit — {@code
 * DoctorResponse.departments} is a lazily-loaded JPA collection, and
 * {@code spring.jpa.open-in-view} (the default, on here) only keeps a Hibernate session
 * open for the lifetime of a real HTTP request, not for a plain method call from a test
 * thread. A direct-call version of this benchmark would throw
 * {@code LazyInitializationException} on the very first (cache-miss) call.
 *
 * <p>Call #1 for a freshly-created doctor is guaranteed a cache miss (nothing's cached it
 * yet) — it pays the full DB round trip plus the lazy {@code departments} collection
 * load. Calls #2..N hit Redis instead, skipping Postgres entirely. The gap between those
 * two numbers <em>is</em> the measurement; there's nothing to assert pass/fail on beyond
 * "it ran," which is exactly why this is {@code @Disabled} by default, same reasoning as
 * the REST-vs-GraphQL benchmark.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled("Manual benchmark for docs/v2/cache-performance-report.md — see class Javadoc")
class CachePerformanceBenchmarkTest {

    private static final int WARM_CALLS = 30;

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String baseUrl;
    private String token;

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
    void measureCacheMissVsCacheHitLatency_andWriteReport() throws Exception {
        baseUrl = "http://localhost:" + port;
        token = login();

        String departmentId = createDepartment();
        String doctorId = createDoctor(departmentId);
        String path = "/api/v1/doctors/" + doctorId;

        // Call #1: guaranteed cache miss — nothing has cached this brand-new doctor yet.
        long missStart = System.nanoTime();
        restGet(path);
        double missMs = (System.nanoTime() - missStart) / 1_000_000.0;

        // Calls #2..N+1: cache hits — the same key, already populated by call #1.
        long[] hitTimingsNs = new long[WARM_CALLS];
        for (int i = 0; i < WARM_CALLS; i++) {
            long t0 = System.nanoTime();
            restGet(path);
            hitTimingsNs[i] = System.nanoTime() - t0;
        }
        double sum = 0;
        for (long t : hitTimingsNs) sum += t;
        double avgHitMs = (sum / hitTimingsNs.length) / 1_000_000.0;
        long[] sorted = hitTimingsNs.clone();
        Arrays.sort(sorted);
        double p95HitMs = sorted[Math.max(0, (int) Math.ceil(0.95 * sorted.length) - 1)] / 1_000_000.0;

        double speedup = missMs / avgHitMs;

        String report = renderReport(missMs, avgHitMs, p95HitMs, speedup);
        System.out.println(report);
        Path reportPath = Path.of("docs", "v2", "cache-performance-report.md");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        assertThat(reportPath).exists();
    }

    // ── HTTP helpers (same shape as RestVsGraphQlBenchmarkTest) ─────────────────

    private String login() {
        String body = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login", new HttpEntity<>(body, headers), String.class);
        return readField(response.getBody(), "/data/token");
    }

    private HttpEntity<String> authEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> restGet(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, authEntity(null), String.class);
    }

    private String createDepartment() {
        String body = "{\"name\":\"Cache Bench Dept " + uniqueDigits(6) + "\"}";
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/v1/departments", authEntity(body), String.class);
        return readField(response.getBody(), "/data/departmentId");
    }

    private String createDoctor(String departmentId) {
        String body = "{\"firstName\":\"Cache\",\"lastName\":\"Bench\",\"email\":\"cachebench"
                + uniqueDigits(8) + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/v1/doctors", authEntity(body), String.class);
        return readField(response.getBody(), "/data/doctorId");
    }

    private String readField(String json, String pointer) {
        try {
            return objectMapper.readTree(json).at(pointer).asText();
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse response: " + json, e);
        }
    }

    // ── Report rendering ─────────────────────────────────────────────────────

    private String renderReport(double missMs, double avgHitMs, double p95HitMs, double speedup) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Cache Performance Report\n\n");
        sb.append("HMS v2's Epic 4 (\"Caching and Performance Enhancement\") asks for measured, ")
                .append("not just implemented, performance improvements from Spring Cache. This measures ")
                .append("`GET /api/v1/doctors/{id}` (`DoctorService.getDoctor`, `@Cacheable(\"doctors\")`, ")
                .append("Redis-backed — see `CacheConfig`) through real HTTP: one guaranteed cache-miss call ")
                .append("against a freshly-created doctor (pays the full Postgres round trip, including the ")
                .append("lazily-loaded `departments` collection), then ").append(WARM_CALLS)
                .append(" guaranteed cache-hit calls against the same id (Redis only, Postgres never touched ")
                .append("again).\n\n");
        sb.append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("  \n**Cache-hit calls measured:** ").append(WARM_CALLS).append("  \n")
                .append("**Environment:** single development machine, real PostgreSQL, real Redis, real HTTP ")
                .append("round trips via a random-port embedded Tomcat (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).\n\n");
        sb.append("## Results\n\n");
        sb.append("| Measurement | Latency (ms) |\n|---|---|\n");
        sb.append(String.format("| Cache miss (1st call — Postgres + Redis write) | %.3f |%n", missMs));
        sb.append(String.format("| Cache hit, avg (Redis read only) | %.3f |%n", avgHitMs));
        sb.append(String.format("| Cache hit, p95 (Redis read only) | %.3f |%n", p95HitMs));
        sb.append(String.format("| **Speedup (miss / avg hit)** | **%.1fx** |%n", speedup));
        sb.append("\n## Analysis\n\n");
        sb.append(String.format("- A cache hit was **%.1fx faster** than the initial cache-miss call on this run.%n",
                speedup));
        sb.append("- Both numbers include the same constant HTTP + Spring MVC dispatch overhead — the gap ")
                .append("between them isolates the caching layer's own effect, not raw request overhead.\n");
        sb.append("- The miss case pays a full connection-pool-to-Postgres round trip, the lazily-loaded ")
                .append("`departments` collection's own query, JPA-to-DTO mapping, and a Redis write to ")
                .append("populate the cache; the hit case pays only a Redis read plus JSON deserialization ")
                .append("(`GenericJackson2JsonRedisSerializer`, see `CacheConfig`) — no SQL, no Hibernate ")
                .append("session work at all.\n");
        sb.append("- This is a single-row, single-key measurement, not a load test — it demonstrates that ")
                .append("caching a single-item lookup measurably helps, not what happens under concurrent ")
                .append("load or with a cold Redis instance under memory pressure.\n");
        sb.append("- This project deliberately caches only single-item lookups (`getUser`/`getDoctor`/")
                .append("`getInvoice`/etc. by id), never a paginated listing — see `docs/performance-report.md`'s ")
                .append("\"What `@Timed` maps onto this report\" section and `UserService.getUsers`'s own ")
                .append("Javadoc for why a whole-table cache doesn't fit this project's write-heavy, ")
                .append("filterable/sortable/paginated access pattern.\n");
        sb.append("\n*Generated by `CachePerformanceBenchmarkTest.measureCacheMissVsCacheHitLatency_andWriteReport` ")
                .append("— re-run it (after commenting out its `@Disabled`) to regenerate this file.*\n");
        return sb.toString();
    }
}
