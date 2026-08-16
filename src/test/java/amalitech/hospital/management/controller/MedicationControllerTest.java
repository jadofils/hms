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
 * Exercises {@link MedicationController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code MedicationServiceTest}); this only needs to
 * reach every controller method body.
 */
class MedicationControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String name = "TestMed" + uniqueDigits(9);

        mockMvc.perform(get("/api/v1/medications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"name\":\"" + name + "\",\"genericName\":\"Testium\",\"form\":\"tablet\",\"unitPrice\":9.99}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String medicationId = created.at("/data/medicationId").asText();

        mockMvc.perform(get("/api/v1/medications/" + medicationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"name\":\"" + name + " Updated\",\"form\":\"capsule\",\"unitPrice\":12.00}";
        mockMvc.perform(put("/api/v1/medications/" + medicationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/medications/" + medicationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getMedication_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/medications/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createMedication_returns409_whenNameTaken() throws Exception {
        String token = adminToken();
        String name = "DupeMed" + uniqueDigits(9);
        String body = "{\"name\":\"" + name + "\"}";

        mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
