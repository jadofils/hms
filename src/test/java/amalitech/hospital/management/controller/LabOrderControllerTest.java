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
 * Exercises {@link LabOrderController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code LabOrderServiceTest}); this only needs to reach
 * every controller method body.
 */
class LabOrderControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String appointmentId = createAppointment(token);
        String doctorId = createDoctor(token);

        mockMvc.perform(get("/api/v1/lab-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"appointmentId\":\"" + appointmentId + "\",\"doctorId\":\"" + doctorId
                + "\",\"testName\":\"Blood Panel\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/lab-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String labOrderId = created.at("/data/labOrderId").asText();

        mockMvc.perform(get("/api/v1/lab-orders/" + labOrderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"appointmentId\":\"" + appointmentId + "\",\"doctorId\":\"" + doctorId
                + "\",\"testName\":\"Blood Panel\",\"status\":\"completed\"}";
        mockMvc.perform(put("/api/v1/lab-orders/" + labOrderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/lab-orders/" + labOrderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getLabOrder_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/lab-orders/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createLabOrder_returns404_whenAppointmentAbsent() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);
        String body = "{\"appointmentId\":\"nonexistent-id\",\"doctorId\":\"" + doctorId + "\",\"testName\":\"X-Ray\"}";
        mockMvc.perform(post("/api/v1/lab-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
