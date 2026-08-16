package amalitech.hospital.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link DoctorScheduleController} through real HTTP — the service layer
 * already has its own thorough unit tests ({@code DoctorScheduleServiceTest}); this only
 * needs to reach every controller method body (HTTP <-> DTO <-> service mapping), so
 * assertions stay light (status codes), not exhaustive behavioral checks.
 */
class DoctorScheduleControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);

        mockMvc.perform(get("/api/v1/doctors/" + doctorId + "/schedules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"dayOfWeek\":\"Mon\",\"startTime\":\"09:00:00\",\"endTime\":\"17:00:00\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/doctors/" + doctorId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String scheduleId = created.at("/data/scheduleId").asText();

        String updateBody = "{\"dayOfWeek\":\"Tue\",\"startTime\":\"10:00:00\",\"endTime\":\"18:00:00\"}";
        mockMvc.perform(put("/api/v1/doctors/" + doctorId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/doctors/" + doctorId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void checkAvailability_throughRealHttpEndpoint() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);

        String createBody = "{\"dayOfWeek\":\"Mon\",\"startTime\":\"09:00:00\",\"endTime\":\"17:00:00\"}";
        mockMvc.perform(post("/api/v1/doctors/" + doctorId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/doctors/" + doctorId + "/schedules/availability?day=Mon&time=10:00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void createSchedule_returns400_whenEndTimeNotAfterStartTime() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);

        String createBody = "{\"dayOfWeek\":\"Mon\",\"startTime\":\"17:00:00\",\"endTime\":\"09:00:00\"}";
        mockMvc.perform(post("/api/v1/doctors/" + doctorId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isBadRequest());
    }
}
