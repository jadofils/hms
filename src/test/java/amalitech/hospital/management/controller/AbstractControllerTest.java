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

    /** Monotonic tie-breaker for {@link #uniqueDigits} — {@code System.nanoTime()} alone
     *  isn't guaranteed unique between two calls a few instructions apart (its actual
     *  clock resolution on some JVM/OS combinations is coarser than one nanosecond), and
     *  the fixture helpers below (e.g. {@link #createPatient}/{@link #createAppointment})
     *  now call it several times in immediate succession per test. */
    private static final java.util.concurrent.atomic.AtomicLong UNIQUE_SEQ = new java.util.concurrent.atomic.AtomicLong();

    /** A short numeric-only string, unique enough per test run to avoid colliding with
     *  unique-constrained columns (phone/username/etc.) left behind by earlier runs —
     *  these tests hit the real DB and don't roll back. */
    protected static String uniqueDigits(int len) {
        String combined = Long.toString(System.nanoTime()) + UNIQUE_SEQ.incrementAndGet();
        return combined.substring(combined.length() - len);
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
        String body = "{\"firstName\":\"Greg\",\"lastName\":\"House\",\"specialization\":\"Diagnostics\","
                + "\"phone\":\"" + uniqueDigits(9) + "\",\"email\":\"doctor" + uniqueDigits(6) + "@example.com\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/doctorId").asText();
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
