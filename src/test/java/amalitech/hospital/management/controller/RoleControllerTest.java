package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
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

        // Permissions are a fixed, system-managed catalog (see PermissionService's
        // Javadoc) — grab one from the already-seeded set rather than creating one.
        String permissionId = fetchASeededPermissionId(token);

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

    @Test
    void createRole_grantsPermissionsInTheSameRequest() throws Exception {
        String token = adminToken();
        String roleName = "TestRole" + uniqueDigits(6);
        String permissionId = fetchASeededPermissionId(token);

        String createRoleBody = "{\"roleName\":\"" + roleName + "\",\"permissionIds\":[\"" + permissionId + "\"]}";
        MvcResult createRoleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleBody))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = objectMapper.readTree(createRoleResult.getResponse().getContentAsString())
                .at("/data/roleId").asText();

        MvcResult permissionsResult = mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode grantedPermissions = objectMapper.readTree(permissionsResult.getResponse().getContentAsString()).at("/data");
        assertThat(grantedPermissions).hasSize(1);
        assertThat(grantedPermissions.get(0).at("/permissionId").asText()).isEqualTo(permissionId);
    }

    @Test
    void createRole_returns404_whenAPermissionIdDoesNotExist() throws Exception {
        String token = adminToken();
        String roleName = "TestRole" + uniqueDigits(6);
        String createRoleBody = "{\"roleName\":\"" + roleName + "\",\"permissionIds\":[\"nonexistent-permission-id\"]}";

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRolePermissionSummary_returnsOkWithArrayBody() throws Exception {
        String token = adminToken();

        MvcResult result = mockMvc.perform(get("/api/v1/roles/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        // Seeded roles always exist (see DataSeeder), so this always returns rows.
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(0);
    }

    @Test
    void getAssignedRoles_returnsOkWithArrayBody() throws Exception {
        String token = adminToken();

        MvcResult result = mockMvc.perform(get("/api/v1/roles/assigned?sort=roleName,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/content");
        // The seeded admin user holds a role (see DataSeeder), so this always returns rows.
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThan(0);
    }

    private String fetchASeededPermissionId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/permissions?size=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/content/0/permissionId").asText();
    }
}
