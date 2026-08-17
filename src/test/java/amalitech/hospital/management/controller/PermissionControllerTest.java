package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link PermissionController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code PermissionServiceTest}); this only needs to
 * reach every controller method body. Read-only: permissions are a fixed, system-managed
 * catalog seeded by {@code DataSeeder} (see {@code PermissionService}'s Javadoc), so these
 * tests read the already-seeded catalog rather than creating a throwaway permission.
 */
class PermissionControllerTest extends AbstractControllerTest {

    @Test
    void getPermissions_returnsTheSeededCatalog() throws Exception {
        String token = adminToken();

        MvcResult result = mockMvc.perform(get("/api/v1/permissions?sort=resource,asc&size=200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThan(0);
    }

    @Test
    void getPermission_returnsMappedResponse_forASeededPermission() throws Exception {
        String token = adminToken();

        MvcResult listResult = mockMvc.perform(get("/api/v1/permissions?size=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String permissionId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .at("/data/content/0/permissionId").asText();

        mockMvc.perform(get("/api/v1/permissions/" + permissionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getPermission_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/permissions/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
