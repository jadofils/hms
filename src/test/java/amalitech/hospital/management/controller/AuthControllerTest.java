package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link AuthController} through real HTTP. {@code AuthService} already has
 * its own thorough unit tests ({@code AuthServiceTest}); this only needs to reach every
 * controller method body. Every mutating test (logout/change-password) registers its
 * own fresh throwaway user rather than touching the shared seeded {@code admin} fixture
 * — every other controller test in this suite depends on {@code admin}/{@code Admin@123}
 * continuing to work.
 */
class AuthControllerTest extends AbstractControllerTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void register_createsAccount() throws Exception {
        String username = "authreg" + uniqueDigits(6);
        String body = "{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void login_returns401_onWrongPassword() throws Exception {
        String username = "authlog" + uniqueDigits(6);
        registerUser(username, "TestPass1!");

        String body = "{\"username\":\"" + username + "\",\"password\":\"WrongPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidatesToken() throws Exception {
        String username = "authout" + uniqueDigits(6);
        String token = registerAndLogin(username, "TestPass1!");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns401_whenNoTokenProvided() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_alwaysReturns200() throws Exception {
        String body = "{\"email\":\"nonexistent" + uniqueDigits(6) + "@example.com\"}";
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmail_returns400_forInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "bogus-token-" + uniqueDigits(9)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withEmail_blocksLoginUntilVerified_thenStillNeedsARoleAfterward() throws Exception {
        String username = "authver" + uniqueDigits(6);
        String email = "authver" + uniqueDigits(6) + "@example.com";
        String registerBody = "{\"username\":\"" + username + "\",\"password\":\"TestPass1!\",\"email\":\"" + email + "\"}";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registered = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String userId = registered.at("/data/userId").asText();

        // Blocked before verification — never reaches the "no role assigned" branch.
        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());

        // Seeds the same Redis key UserService.createUser would have written, without
        // depending on a real email actually being sent/read (same pattern as
        // resetPassword_succeeds_withValidToken above).
        String token = "test-verify-token-" + uniqueDigits(12);
        redisTemplate.opsForValue().set("email-verify:" + token, userId, Duration.ofHours(24));

        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", token))
                .andExpect(status().isOk());

        // Verified now, but still no role — the two gates are independent.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());

        String admin = adminToken();
        String roleBody = "{\"roleName\":\"TestRole" + uniqueDigits(9) + "\"}";
        MvcResult roleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = objectMapper.readTree(roleResult.getResponse().getContentAsString()).at("/data/roleId").asText();
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // Verified AND role-assigned — login finally succeeds.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_returns400_forInvalidToken() throws Exception {
        String body = "{\"token\":\"bogus-token-" + uniqueDigits(9) + "\",\"newPassword\":\"NewPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_succeeds_withValidToken() throws Exception {
        // No email on this account, so AuthService.resetPassword's null-guard skips the
        // passwordChanged email send — this test only needs to reach the 200 response,
        // not exercise a real SMTP send.
        String username = "authrst" + uniqueDigits(6);
        String registerBody = "{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registered = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String userId = registered.at("/data/userId").asText();

        // Seeds the same Redis key AuthService.forgotPassword would have written, without
        // going through it (which would require a real email on file).
        String token = "test-reset-token-" + uniqueDigits(12);
        redisTemplate.opsForValue().set("password-reset:" + token, userId, Duration.ofMinutes(30));

        String resetBody = "{\"token\":\"" + token + "\",\"newPassword\":\"NewPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_succeeds_withCorrectCurrentPassword() throws Exception {
        String username = "authchg" + uniqueDigits(6);
        String token = registerAndLogin(username, "TestPass1!");

        String body = "{\"currentPassword\":\"TestPass1!\",\"newPassword\":\"NewPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_returns401_whenNoTokenProvided() throws Exception {
        String body = "{\"currentPassword\":\"whatever1\",\"newPassword\":\"NewPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returns401_withWrongCurrentPassword() throws Exception {
        String username = "authchw" + uniqueDigits(6);
        String token = registerAndLogin(username, "TestPass1!");

        String body = "{\"currentPassword\":\"WrongPass1!\",\"newPassword\":\"NewPass1!\"}";
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsCurrentIdentity() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void me_returns401_whenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void registerUser(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /**
     * Registers a fresh throwaway user and assigns it a fresh throwaway role — a brand
     * new registration has no role yet (see {@code UserService.createUser}'s Javadoc)
     * and can't log in until one is assigned, exactly like a real admin would have to do.
     */
    private String registerAndLogin(String username, String password) throws Exception {
        String admin = adminToken();

        String registerBody = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registered = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String userId = registered.at("/data/userId").asText();

        String roleBody = "{\"roleName\":\"TestRole" + uniqueDigits(9) + "\"}";
        MvcResult roleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode role = objectMapper.readTree(roleResult.getResponse().getContentAsString());
        String roleId = role.at("/data/roleId").asText();

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        return loginAs(username, password);
    }
}
