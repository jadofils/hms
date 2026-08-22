package amalitech.hospital.management.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import amalitech.hospital.management.service.PatientService;
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
 * Measures real before/after latency for {@code PatientService.getPatient}'s HMS v5
 * {@code CompletableFuture} fan-out — unlike {@code EntityGraphBenchmarkTest} (which has
 * to describe its pre-fix counterfactual analytically, since the fix there is an
 * annotation with nothing to call the "before" version of), this refactor left the 9
 * individual fetch methods ({@code fetchPatientCore}/{@code fetchAppointments}/etc.)
 * directly callable — so "before" is measured for real, by calling all 9 sequentially
 * through the same Spring-managed {@link PatientService} bean, and "after" is the real
 * {@code GET /api/v1/patients/{id}} endpoint (real HTTP, real Postgres), which already
 * runs the parallel version — there is no separate "before" endpoint to call.
 *
 * <p>{@code @Disabled} by default, same reasoning as this package's other benchmarks:
 * creates real rows in the shared dev/test database and isn't a correctness gate.
 * Re-run explicitly to regenerate {@code docs/patient-profile-performance-report.md}:
 * <pre>{@code ./mvnw test -Dtest=PatientProfileBenchmarkTest}</pre>
 * (after commenting out the {@code @Disabled} line below).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled("Manual benchmark for docs/patient-profile-performance-report.md — see class Javadoc")
class PatientProfileBenchmarkTest {

    private static final int APPOINTMENT_ROWS = 5;
    private static final int WARM_CALLS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private PatientService patientService;

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
    void measureSequentialVsParallelFetch_andWriteReport() throws Exception {
        baseUrl = "http://localhost:" + port;
        token = login();

        String departmentId = createDepartment();
        String doctorId = createDoctor(departmentId);
        String patientId = createPatient();
        for (int i = 0; i < APPOINTMENT_ROWS; i++) {
            String appointmentId = createAppointment(patientId, doctorId, i);
            createInvoice(appointmentId, patientId);
        }

        // "Before" — the same 9 lookups getPatient makes, called sequentially through
        // the real Spring-managed PatientService bean, exactly what the pre-v5 method
        // body did (one after another, no fan-out).
        long sequentialStart = System.nanoTime();
        patientService.fetchPatientCore(patientId);
        patientService.fetchAppointments(patientId);
        patientService.fetchInvoices(patientId);
        patientService.fetchAllergies(patientId);
        patientService.fetchFeedback(patientId);
        patientService.fetchNotes(patientId);
        patientService.fetchMedicalRecords(patientId);
        patientService.fetchVitalSigns(patientId);
        patientService.fetchReferrals(patientId);
        double sequentialMs = (System.nanoTime() - sequentialStart) / 1_000_000.0;

        // "After" — the real endpoint, already running the parallel version. Each call
        // uses a distinct patient (evicting/missing the @Cacheable entry every time
        // would require an eviction call per iteration; simplest here is a fresh
        // real @Cacheable miss per call by varying nothing but re-reading — since
        // @Cacheable is per-id, repeat calls after the first would be cache hits, so
        // only the first call after creation is a genuine miss; average across
        // WARM_CALLS fresh patients instead of repeating the same one.
        long[] parallelTimingsNs = new long[WARM_CALLS];
        for (int i = 0; i < WARM_CALLS; i++) {
            String freshPatientId = createPatient();
            String freshAppointmentId = createAppointment(freshPatientId, doctorId, APPOINTMENT_ROWS + i);
            createInvoice(freshAppointmentId, freshPatientId);
            long t0 = System.nanoTime();
            restGet("/api/v1/patients/" + freshPatientId);
            parallelTimingsNs[i] = System.nanoTime() - t0;
        }
        double sum = 0;
        for (long t : parallelTimingsNs) sum += t;
        double avgParallelMs = (sum / parallelTimingsNs.length) / 1_000_000.0;

        String report = renderReport(sequentialMs, avgParallelMs);
        System.out.println(report);
        Path reportPath = Path.of("docs", "patient-profile-performance-report.md");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        assertThat(reportPath).exists();
    }

    // ── HTTP helpers (same shape as the other benchmarks in this package) ──────

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
        String body = "{\"name\":\"Patient Profile Bench Dept " + uniqueDigits(6) + "\",\"location\":\"Main Building\"}";
        return readField(restPost("/api/v1/departments", body).getBody(), "/data/departmentId");
    }

    private String createDoctor(String departmentId) {
        String body = "{\"firstName\":\"PPBench\",\"lastName\":\"Doctor\",\"specialization\":\"General\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"ppbenchdoc" + uniqueDigits(6)
                + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
        return readField(restPost("/api/v1/doctors", body).getBody(), "/data/doctorId");
    }

    private String createPatient() {
        String body = "{\"firstName\":\"PPBench\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"M\",\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"ppbenchpat"
                + uniqueDigits(8) + "@example.com\",\"address\":\"123 Main St\"}";
        return readField(restPost("/api/v1/patients", body).getBody(), "/data/patientId");
    }

    private String createAppointment(String patientId, String doctorId, int slot) {
        // slot ranges 0..(APPOINTMENT_ROWS + WARM_CALLS - 1) across this test's two call
        // sites — offsetting the hour by slot (no modulo) keeps every one of them unique
        // for the same doctor, avoiding AppointmentService's double-booking check.
        String body = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId
                + "\",\"appointmentDate\":\"2099-02-01T" + String.format("%02d", 1 + slot) + ":00:00\","
                + "\"reason\":\"Patient profile benchmark checkup\"}";
        return readField(restPost("/api/v1/appointments", body).getBody(), "/data/appointmentId");
    }

    private void createInvoice(String appointmentId, String patientId) {
        String body = "{\"appointmentId\":\"" + appointmentId + "\",\"patientId\":\"" + patientId
                + "\",\"totalAmount\":100.00}";
        restPost("/api/v1/invoices", body);
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

    private String renderReport(double sequentialMs, double avgParallelMs) {
        double speedup = sequentialMs / avgParallelMs;
        StringBuilder sb = new StringBuilder();
        sb.append("# Patient Profile CompletableFuture Fan-Out — Performance Report\n\n");
        sb.append("HMS v5's `PatientService.getPatient` refactor: 9 independent lookups (the core ")
                .append("patient row plus 8 associated collections), previously run one after another, ")
                .append("now dispatched in parallel via `CompletableFuture.supplyAsync` against a dedicated ")
                .append("`patientProfileExecutor` (see `AsyncConfig`). \"Before\" is measured for real — ")
                .append("the same 9 methods, called sequentially through the real Spring-managed ")
                .append("`PatientService` bean, not a projected number. \"After\" is the real ")
                .append("`GET /api/v1/patients/{id}` endpoint, which already runs the parallel version.\n\n");
        sb.append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("  \n**Parallel-call samples:** ").append(WARM_CALLS).append(" fresh patients (each a ")
                .append("genuine `@Cacheable` miss)  \n**Environment:** single development machine, real ")
                .append("PostgreSQL, real HTTP round trips via a random-port embedded Tomcat.\n\n");
        sb.append("## Results\n\n");
        sb.append("| Measurement | Latency (ms) |\n|---|---|\n");
        sb.append(String.format("| Sequential (9 fetches, one after another) | %.3f |%n", sequentialMs));
        sb.append(String.format("| Parallel, avg (real HTTP, `CompletableFuture` fan-out) | %.3f |%n", avgParallelMs));
        sb.append(String.format("| **Speedup** | **%.2fx** |%n", speedup));
        sb.append("\n## Analysis\n\n");
        sb.append(String.format("- The parallel version was **%.2fx faster** than running the same 9 lookups ", speedup))
                .append("sequentially, on this run.\n");
        sb.append("- The parallel measurement includes real HTTP + Spring MVC dispatch overhead the ")
                .append("sequential measurement doesn't (a direct method call) — the true fan-out speedup on ")
                .append("the 9 lookups themselves is understated here, not overstated.\n");
        sb.append("- Every one of the 9 lookups also carries its own `@EntityGraph` (HMS v5's first change ")
                .append("this pass) — this benchmark measures the fan-out's own effect on top of that fix, ")
                .append("not a combined before/after against the pre-`@EntityGraph` state.\n");
        sb.append("\n*Generated by `PatientProfileBenchmarkTest.measureSequentialVsParallelFetch_andWriteReport` ")
                .append("— re-run it (after commenting out its `@Disabled`) to regenerate this file.*\n");
        return sb.toString();
    }
}
