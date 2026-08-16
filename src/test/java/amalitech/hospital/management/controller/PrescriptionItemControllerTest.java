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
 * Exercises {@link PrescriptionItemController} through real HTTP — the service layer
 * already has its own thorough unit tests ({@code PrescriptionItemServiceTest}); this
 * only needs to reach every controller method body.
 */
class PrescriptionItemControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String prescriptionId = createPrescription(token);
        String medicationId = createMedication(token);
        String base = "/api/v1/prescriptions/" + prescriptionId + "/items";

        mockMvc.perform(get(base).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"medicationId\":\"" + medicationId + "\",\"dosage\":\"500mg\","
                + "\"quantity\":10,\"instructions\":\"Twice daily\"}";
        MvcResult createResult = mockMvc.perform(post(base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String itemId = created.at("/data/itemId").asText();

        String updateBody = "{\"medicationId\":\"" + medicationId + "\",\"dosage\":\"250mg\",\"quantity\":5}";
        mockMvc.perform(put(base + "/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete(base + "/" + itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getItems_returns404_whenPrescriptionAbsent() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/prescriptions/nonexistent-id/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createItem_returns404_whenMedicationAbsent() throws Exception {
        String token = adminToken();
        String prescriptionId = createPrescription(token);
        String body = "{\"medicationId\":\"nonexistent-id\",\"quantity\":1}";
        mockMvc.perform(post("/api/v1/prescriptions/" + prescriptionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
