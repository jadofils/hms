package amalitech.hospital.management.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link AuthorizationAspect} through the real Spring AOP proxy, end to end
 * via HTTP — a plain Mockito unit test can't verify {@code @Before("@annotation(...)")}
 * matching or interception (see CLAUDE.md's Testing section: aspects need a real proxy).
 * Logs in as actual seeded users ({@code DataSeeder}) via the real
 * {@code /api/v1/auth/login} flow to get real JWTs, same real-Postgres setup
 * {@code HmsApplicationTests} already relies on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationAspectTest {

    @Autowired private MockMvc mockMvc;
    // Not a Spring-managed bean in this app's configuration — a plain local instance is
    // all this test needs (simple username/password/token fields, no custom modules).
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void noToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctRole_isAllowedThroughToTheController() throws Exception {
        String token = loginAs("admin", "Admin@123");

        mockMvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongRole_isRejectedWith403NamingTheMissingPermission() throws Exception {
        // pharmacist1 only has patients:read/doctors:read (see DataSeeder.ROLE_GRANTS) —
        // no patients:delete.
        String token = loginAs("pharmacist1", "Pharmacist@123");

        mockMvc.perform(delete("/api/v1/patients/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("patients:delete")));
    }

    @Test
    void roleWithGrantedPermission_isNotBlockedByAuthorization() throws Exception {
        // receptionist1 has patients:create — asserting only that authorization itself
        // passes (not a 401/403); the request's actual outcome beyond that (e.g. 400 on
        // an empty body) is PatientServiceTest's/validation's concern, not this one's.
        String token = loginAs("receptionist1", "Reception@123");

        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    private String loginAs(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }
}
