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
 * Exercises {@link PermissionController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code PermissionServiceTest}); this only needs to
 * reach every controller method body (HTTP <-> DTO <-> service mapping), so assertions
 * stay light (status codes), not exhaustive behavioral checks.
 */
class PermissionControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String resource = "test-resource-" + uniqueDigits(9);

        mockMvc.perform(get("/api/v1/permissions?sort=resource,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"resource\":\"" + resource + "\",\"action\":\"read\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String permissionId = created.at("/data/permissionId").asText();

        mockMvc.perform(get("/api/v1/permissions/" + permissionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"resource\":\"" + resource + "\",\"action\":\"write\"}";
        mockMvc.perform(put("/api/v1/permissions/" + permissionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/permissions/" + permissionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getPermission_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/permissions/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
