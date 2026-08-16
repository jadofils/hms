package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link NotificationController} through real HTTP — the service layer
 * already has its own thorough unit tests ({@code NotificationServiceTest}); this only
 * needs to reach every controller method body.
 */
class NotificationControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"type\":\"appointment-created\",\"recipients\":[\"user-2\"],\"priority\":\"high\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String notificationId = created.at("/data/notificationId").asText();

        mockMvc.perform(get("/api/v1/notifications/" + notificationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"type\":\"appointment-updated\",\"recipients\":[\"user-2\",\"user-3\"],\"priority\":\"low\"}";
        mockMvc.perform(put("/api/v1/notifications/" + notificationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").exists());

        mockMvc.perform(delete("/api/v1/notifications/" + notificationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getNotification_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/notifications/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createNotification_returns404_whenActorAbsent() throws Exception {
        String token = adminToken();
        String body = "{\"type\":\"test\",\"actorUserId\":\"nonexistent-id\",\"recipients\":[\"user-2\"]}";
        mockMvc.perform(post("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(patch("/api/v1/notifications/nonexistent-id/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
