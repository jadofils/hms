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
 * Exercises {@link PrescriptionController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code PrescriptionServiceTest}); this only needs to
 * reach every controller method body.
 */
class PrescriptionControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String appointmentId = createAppointment(token);

        mockMvc.perform(get("/api/v1/prescriptions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"appointmentId\":\"" + appointmentId + "\",\"dateIssued\":\"2020-01-01\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String prescriptionId = created.at("/data/prescriptionId").asText();

        mockMvc.perform(get("/api/v1/prescriptions/" + prescriptionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"appointmentId\":\"" + appointmentId + "\",\"dateIssued\":\"2020-02-01\"}";
        mockMvc.perform(put("/api/v1/prescriptions/" + prescriptionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/prescriptions/" + prescriptionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getPrescription_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/prescriptions/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPrescription_returns404_whenAppointmentAbsent() throws Exception {
        String token = adminToken();
        String body = "{\"appointmentId\":\"nonexistent-id\"}";
        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
