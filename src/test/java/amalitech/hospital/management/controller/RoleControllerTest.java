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
 * Exercises {@link RoleController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code RoleServiceTest}); this only needs to reach
 * every controller method body (HTTP <-> DTO <-> service mapping), so assertions stay
 * light (status codes), not exhaustive behavioral checks.
 */
class RoleControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String roleName = "TestRole" + uniqueDigits(6);

        mockMvc.perform(get("/api/v1/roles?sort=roleName,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"roleName\":\"" + roleName + "\",\"description\":\"A throwaway test role\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String roleId = created.at("/data/roleId").asText();

        mockMvc.perform(get("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updatedRoleName = roleName + "-updated";
        String updateBody = "{\"roleName\":\"" + updatedRoleName + "\",\"description\":\"Updated description\"}";
        mockMvc.perform(put("/api/v1/roles/" + roleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getRole_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/roles/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantAndRevokePermission_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String roleName = "TestRole" + uniqueDigits(6);

        String createRoleBody = "{\"roleName\":\"" + roleName + "\",\"description\":\"Role for permission grant test\"}";
        MvcResult createRoleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdRole = objectMapper.readTree(createRoleResult.getResponse().getContentAsString());
        String roleId = createdRole.at("/data/roleId").asText();

        String resource = "test-resource-" + uniqueDigits(9);
        String createPermissionBody = "{\"resource\":\"" + resource + "\",\"action\":\"read\"}";
        MvcResult createPermissionResult = mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPermissionBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdPermission = objectMapper.readTree(createPermissionResult.getResponse().getContentAsString());
        String permissionId = createdPermission.at("/data/permissionId").asText();

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
