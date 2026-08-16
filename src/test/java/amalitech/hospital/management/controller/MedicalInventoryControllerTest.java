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
 * Exercises {@link MedicalInventoryController} through real HTTP — the service layer
 * already has its own thorough unit tests ({@code MedicalInventoryServiceTest}); this
 * only needs to reach every controller method body.
 */
class MedicalInventoryControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String medicationId = createMedication(token);

        mockMvc.perform(get("/api/v1/medical-inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"medicationId\":\"" + medicationId + "\",\"batchNumber\":\"B123\","
                + "\"expiryDate\":\"2099-01-01\",\"quantityInStock\":50,\"reorderLevel\":5,\"supplier\":\"Acme\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/medical-inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String inventoryId = created.at("/data/inventoryId").asText();

        mockMvc.perform(get("/api/v1/medical-inventory/" + inventoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"medicationId\":\"" + medicationId + "\",\"expiryDate\":\"2099-06-01\","
                + "\"quantityInStock\":30,\"reorderLevel\":10}";
        mockMvc.perform(put("/api/v1/medical-inventory/" + inventoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/medical-inventory/" + inventoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getInventoryRecord_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/medical-inventory/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInventoryRecord_returns404_whenMedicationAbsent() throws Exception {
        String token = adminToken();
        String body = "{\"medicationId\":\"nonexistent-id\",\"expiryDate\":\"2099-01-01\"}";
        mockMvc.perform(post("/api/v1/medical-inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
