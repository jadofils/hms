package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link SystemLogController} through real HTTP. Unlike every other
 * controller test, there's no create endpoint to seed a fixture through — {@code
 * SystemLog} rows only ever come from {@code LoggingAspect} catching a real thrown
 * exception (see {@code SystemLogService}'s Javadoc), so these tests deliberately
 * trigger one first (a 404 against an unrelated resource) rather than reaching into
 * {@code SystemLogRepository} directly.
 */
class SystemLogControllerTest extends AbstractControllerTest {

    @Test
    void getSystemLogs_returnsOk_unfiltered() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/system-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getSystemLogs_returnsBadRequest_forAnUnrecognizedLogLevel() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/system-logs?logLevel=bogus").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSystemLog_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/system-logs/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSystemLogs_surfacesARealErrorRow_triggeredThroughAnUnrelatedEndpoint() throws Exception {
        String token = adminToken();

        // Any service throwing an exception writes a real "ERROR"-level SystemLog row
        // via LoggingAspect — RoleService.findRoleOrThrow's own NotFoundException here.
        mockMvc.perform(get("/api/v1/roles/nonexistent-role-for-system-log-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        MvcResult listResult = mockMvc.perform(
                        get("/api/v1/system-logs?logLevel=ERROR&source=RoleService&size=5&sort=createdAt,desc")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .at("/data/content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThan(0);
        assertThat(content.get(0).at("/logLevel").asText()).isEqualTo("ERROR");
        assertThat(content.get(0).at("/source").asText()).contains("RoleService");

        String logId = content.get(0).at("/logId").asText();
        mockMvc.perform(get("/api/v1/system-logs/" + logId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
