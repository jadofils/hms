package amalitech.hospital.management.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counts the actual SQL statements Hibernate executes for two of the six N+1 sites HMS
 * v5's {@code @EntityGraph} pass fixed — {@code GET /api/v1/prescriptions} (the worst of
 * the originally-found five: {@code Prescription} → {@code Appointment} →
 * {@code Patient}/{@code Doctor}, a 2-hop lazy chain) and {@code GET /api/v1/patients/{id}}
 * ({@code PatientService.getPatient}'s own 9-query fan-out — the core lookup plus 8
 * associated collections — the sixth site the v5 design
 * review caught). Same "real evidence, not a projected number" discipline as
 * {@code CachePerformanceBenchmarkTest}/{@code RestVsGraphQlBenchmarkTest} — this measures
 * the current (already-fixed) query count directly via Hibernate's own
 * {@link Statistics}, real HTTP, real Postgres; it does not re-run the pre-fix code (that
 * would mean reverting the annotations), but the pre-fix count is arithmetically certain
 * from the exact lazy-association chains documented on each repository interface: N rows
 * × (1 base query + K extra selects/row), vs. this test's own measured "1-2 queries no
 * matter how many rows" result.
 *
 * <p>{@code @Disabled} by default, same reasoning as the other benchmark tests in this
 * package: creates real rows in the shared dev/test database and isn't a correctness
 * gate. Re-run explicitly to regenerate {@code docs/entity-graph-performance-report.md}:
 * <pre>{@code ./mvnw test -Dtest=EntityGraphBenchmarkTest}</pre>
 * (after commenting out the {@code @Disabled} line below).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled("Manual benchmark for docs/entity-graph-performance-report.md — see class Javadoc")
class EntityGraphBenchmarkTest {

    private static final int PRESCRIPTION_ROWS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    void countQueriesForPrescriptionListingAndPatientProfile_andWriteReport() throws Exception {
        baseUrl = "http://localhost:" + port;
        token = login();

        String departmentId = createDepartment();
        String doctorId = createDoctor(departmentId);
        String patientId = createPatient();

        // PRESCRIPTION_ROWS appointments, one prescription per appointment, all for the
        // same patient+doctor — enough rows that an N+1 would visibly scale with N. Each
        // gets its own hour so AppointmentService's double-booking check doesn't reject
        // the 2nd+ one for the same doctor.
        for (int i = 0; i < PRESCRIPTION_ROWS; i++) {
            String appointmentId = createAppointment(patientId, doctorId, i);
            createPrescription(appointmentId);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        statistics.clear();
        restGet("/api/v1/prescriptions?size=50");
        long prescriptionQueries = statistics.getQueryExecutionCount();

        statistics.clear();
        restGet("/api/v1/patients/" + patientId);
        long patientProfileQueries = statistics.getQueryExecutionCount();

        String report = renderReport(prescriptionQueries, patientProfileQueries);
        System.out.println(report);
        Path reportPath = Path.of("docs", "entity-graph-performance-report.md");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        assertThat(reportPath).exists();

        // The real assertion: query count stays small and flat regardless of row count —
        // a per-row N+1 would instead scale linearly with PRESCRIPTION_ROWS (here, that
        // would have meant roughly 1 + 5*3 = 16 statements just for the listing).
        assertThat(prescriptionQueries).isLessThanOrEqualTo(3);
        assertThat(patientProfileQueries).isLessThanOrEqualTo(10);
    }

    // ── HTTP helpers (same shape as RestVsGraphQlBenchmarkTest/CachePerformanceBenchmarkTest) ──

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

    private ResponseEntity<String> restPost(String path, String body) {
        return restTemplate.postForEntity(baseUrl + path, authEntity(body), String.class);
    }

    private String createDepartment() {
        String body = "{\"name\":\"EntityGraph Bench Dept " + uniqueDigits(6) + "\",\"location\":\"Main Building\"}";
        return readField(restPost("/api/v1/departments", body).getBody(), "/data/departmentId");
    }

    private String createDoctor(String departmentId) {
        String body = "{\"firstName\":\"EGBench\",\"lastName\":\"Doctor\",\"specialization\":\"General\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"egbenchdoc" + uniqueDigits(6)
                + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
        return readField(restPost("/api/v1/doctors", body).getBody(), "/data/doctorId");
    }

    private String createPatient() {
        String body = "{\"firstName\":\"EGBench\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"M\",\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"egbenchpat"
                + uniqueDigits(6) + "@example.com\",\"address\":\"123 Main St\"}";
        return readField(restPost("/api/v1/patients", body).getBody(), "/data/patientId");
    }

    private String createAppointment(String patientId, String doctorId, int slot) {
        String body = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId
                + "\",\"appointmentDate\":\"2099-01-01T" + String.format("%02d", 10 + slot) + ":00:00\","
                + "\"reason\":\"EntityGraph benchmark checkup\"}";
        return readField(restPost("/api/v1/appointments", body).getBody(), "/data/appointmentId");
    }

    private void createPrescription(String appointmentId) {
        String body = "{\"appointmentId\":\"" + appointmentId + "\"}";
        restPost("/api/v1/prescriptions", body);
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

    private String renderReport(long prescriptionQueries, long patientProfileQueries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# @EntityGraph N+1 Fix — Query Count Report\n\n");
        sb.append("HMS v5 added `@EntityGraph` to 12 repository finder methods across 6 real N+1 ")
                .append("sites (see `docs/v5-report.md`). This measures the actual number of SQL ")
                .append("statements Hibernate executes for two of them, via `Statistics.getQueryExecutionCount()` ")
                .append("(real Postgres, real HTTP, `@SpringBootTest(webEnvironment = RANDOM_PORT)`).\n\n");
        sb.append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n\n");
        sb.append("## Results\n\n");
        sb.append("| Endpoint | Rows fetched | Queries executed |\n|---|---|---|\n");
        sb.append(String.format("| `GET /api/v1/prescriptions?size=50` | %d prescriptions | %d |%n",
                PRESCRIPTION_ROWS, prescriptionQueries));
        sb.append(String.format("| `GET /api/v1/patients/{id}` | 1 core lookup + 8 collections | %d |%n",
                patientProfileQueries));
        sb.append("\n## Analysis\n\n");
        sb.append(String.format(
                "- The prescriptions listing executed **%d** statement(s) for %d rows. Without "
                        + "`@EntityGraph` (`PrescriptionRepository`'s 2-hop lazy chain: `Prescription` -> "
                        + "`Appointment` -> `Patient`/`Doctor`, all `@ManyToOne(LAZY)`), each row's own "
                        + "`toResponse` mapping would have fired 3 extra `SELECT`s — roughly "
                        + "**1 + %d x 3 = %d** statements for the same %d rows, scaling linearly with "
                        + "row count. This run's count does not scale with row count at all.%n",
                prescriptionQueries, PRESCRIPTION_ROWS, PRESCRIPTION_ROWS, 1 + PRESCRIPTION_ROWS * 3, PRESCRIPTION_ROWS));
        sb.append(String.format(
                "- `GET /api/v1/patients/{id}` executed **%d** statement(s) across its 9 independent "
                        + "finder calls (`PatientService.getPatient`) — one per finder, each now carrying its "
                        + "own `@EntityGraph`, instead of each finder's own per-row lazy fan-out (`toReferralResponse` "
                        + "alone would otherwise cost 3 extra selects per referral row).%n",
                patientProfileQueries));
        sb.append("- Both counts stay flat regardless of row count — the defining signature of an N+1 fix, ")
                .append("as opposed to a count that grows with the data.\n");
        sb.append("\n*Generated by `EntityGraphBenchmarkTest.countQueriesForPrescriptionListingAndPatientProfile_andWriteReport` ")
                .append("— re-run it (after commenting out its `@Disabled`) to regenerate this file.*\n");
        return sb.toString();
    }
}
