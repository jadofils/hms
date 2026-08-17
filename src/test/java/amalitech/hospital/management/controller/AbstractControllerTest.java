package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for controller-level integration tests — real Spring context, real
 * Postgres (same setup {@code HmsApplicationTests}/{@code AuthorizationAspectTest}
 * already rely on), real HTTP through {@link MockMvc}. Unlike the service-layer unit
 * tests (manually-constructed services, mocked collaborators — see CLAUDE.md's Testing
 * section), these exist specifically to exercise the controller mapping code itself
 * (HTTP <-> DTO <-> service call), which the service tests never touch.
 *
 * Every subclass authenticates as the seeded {@code admin} user (see
 * {@code DataSeeder}) — it holds every permission, so tests here are never blocked by
 * {@code AuthorizationAspect}; that mechanism has its own dedicated coverage in
 * {@code AuthorizationAspectTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    // Not a Spring-managed bean in this app's configuration — a plain local instance is
    // all these tests need (simple request/response bodies, no custom modules).
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected String adminToken() throws Exception {
        return loginAs("admin", "Admin@123");
    }

    protected String loginAs(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }

    /** Baseline captured once at class-load time — provides entropy against
     *  unique-constrained columns (phone/username/etc.) left behind by earlier runs
     *  (these tests hit the real DB and don't roll back), without being read again on
     *  every call the way the old formula read {@code System.nanoTime()} per call. */
    private static final long RUN_OFFSET = System.nanoTime();

    /** Monotonic per-call counter. Combined with {@link #RUN_OFFSET} via addition (not
     *  string concatenation — see below), {@code RUN_OFFSET + n} is itself a strictly
     *  increasing integer sequence, so its last {@code len} digits can only repeat once
     *  10^len calls have been made — never in one test run.
     *
     *  <p>The previous formula concatenated {@code Long.toString(System.nanoTime())} with
     *  the counter as strings and took the trailing substring: reading
     *  {@code System.nanoTime()} fresh on every call meant two calls a few instructions
     *  apart could occasionally observe non-monotonic values (TSC drift across CPU cores
     *  is a known issue on some Windows/JVM combinations), and — because the counter's own
     *  string length grows over a long test run — the digit boundary between the two
     *  concatenated parts could shift between calls, letting two genuinely different
     *  {@code (nanoTime, counter)} pairs land on the same trailing substring. This
     *  manifested as a real, reproducible duplicate-email 409 in
     *  {@code DoctorControllerTest}/{@code LabOrderControllerTest} once a fixture started
     *  calling this several times in a single method (see {@link #createDoctor}). Pure
     *  integer addition has no such boundary to shift. */
    private static final java.util.concurrent.atomic.AtomicLong UNIQUE_SEQ = new java.util.concurrent.atomic.AtomicLong();

    /** A short numeric-only string, unique enough per test run to avoid colliding with
     *  unique-constrained columns (phone/username/etc.) left behind by earlier runs. */
    protected static String uniqueDigits(int len) {
        long value = RUN_OFFSET + UNIQUE_SEQ.incrementAndGet();
        String digits = Long.toString(Math.abs(value));
        if (digits.length() < len) {
            digits = "0".repeat(len - digits.length()) + digits;
        }
        return digits.substring(digits.length() - len);
    }

    // ── Shared fixtures ──────────────────────────────────────────────────────
    // Several newer domains (pharmacy/lab/finance) reference a Patient/Doctor/Appointment/
    // Medication by id — these create a real throwaway one via the actual API (not a
    // repository shortcut) so every layer, including validation, is exercised the same
    // way a real caller would hit it.

    protected String createPatient(String token) throws Exception {
        String body = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"M\",\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"patient"
                + uniqueDigits(6) + "@example.com\",\"address\":\"123 Main St\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/patientId").asText();
    }

    protected String createDoctor(String token) throws Exception {
        // Every doctor must belong to at least one department (DoctorService.createDoctor
        // enforces this) — so this fixture creates a throwaway department first, the same
        // way it creates a throwaway patient/doctor for prescription/lab-order fixtures.
        String departmentId = createDepartment(token);
        String body = "{\"firstName\":\"Greg\",\"lastName\":\"House\",\"specialization\":\"Diagnostics\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"doctor" + uniqueDigits(6)
                + "@example.com\",\"departmentIds\":[\"" + departmentId + "\"]}";
        MvcResult result = mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/doctorId").asText();
    }

    protected String createDepartment(String token) throws Exception {
        String body = "{\"name\":\"Test Department " + uniqueDigits(6) + "\",\"location\":\"Main Building\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/departmentId").asText();
    }

    protected String createAppointment(String token) throws Exception {
        String patientId = createPatient(token);
        String doctorId = createDoctor(token);
        String body = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId
                + "\",\"appointmentDate\":\"2099-01-01T10:00:00\",\"reason\":\"Checkup\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/appointmentId").asText();
    }

    protected String createMedication(String token) throws Exception {
        String body = "{\"name\":\"TestMed" + uniqueDigits(9) + "\",\"form\":\"tablet\",\"unitPrice\":5.50}";
        MvcResult result = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/medicationId").asText();
    }

    protected String createPrescription(String token) throws Exception {
        String appointmentId = createAppointment(token);
        String body = "{\"appointmentId\":\"" + appointmentId + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/prescriptionId").asText();
    }

    protected String createLabOrder(String token) throws Exception {
        String appointmentId = createAppointment(token);
        String doctorId = createDoctor(token);
        String body = "{\"appointmentId\":\"" + appointmentId + "\",\"doctorId\":\"" + doctorId
                + "\",\"testName\":\"Blood Panel\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/lab-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/labOrderId").asText();
    }
}
