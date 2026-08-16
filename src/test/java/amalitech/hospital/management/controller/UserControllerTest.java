package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link UserController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code UserServiceTest}); this only needs to reach
 * every controller method body (HTTP <-> DTO <-> service mapping), so assertions stay
 * light (status codes), not exhaustive behavioral checks.
 */
class UserControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String username = "testuser" + uniqueDigits(6);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String userId = created.at("/data/userId").asText();

        mockMvc.perform(get("/api/v1/users/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updatedUsername = "testuser" + uniqueDigits(6);
        String updateBody = "{\"username\":\"" + updatedUsername + "\",\"password\":\"TestPass1!\"}";
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createUser_returns409_whenEmailTaken() throws Exception {
        String token = adminToken();
        String username = "testuser" + uniqueDigits(6);
        String email = username + "@example.com";
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + email + "\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String secondBody = "{\"username\":\"testuser" + uniqueDigits(6) + "\",\"email\":\"" + email + "\"}";
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict());
    }

    @Test
    void createUser_returns400_whenEmailMissing() throws Exception {
        String token = adminToken();
        String body = "{\"username\":\"testuser" + uniqueDigits(6) + "\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/users/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignAndRevokeRole_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String username = "testuser" + uniqueDigits(6);

        String createUserBody = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\"}";
        MvcResult createUserResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdUser = objectMapper.readTree(createUserResult.getResponse().getContentAsString());
        String userId = createdUser.at("/data/userId").asText();

        String roleName = "TestRole" + uniqueDigits(6);
        String createRoleBody = "{\"roleName\":\"" + roleName + "\",\"description\":\"Throwaway test role\"}";
        MvcResult createRoleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdRole = objectMapper.readTree(createRoleResult.getResponse().getContentAsString());
        String roleId = createdRole.at("/data/roleId").asText();

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
