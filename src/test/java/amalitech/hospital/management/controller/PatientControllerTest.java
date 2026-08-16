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
 * Exercises {@link PatientController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code PatientServiceTest}); this only needs to reach
 * every controller method body (HTTP <-> DTO <-> service mapping), so assertions stay
 * light (status codes), not exhaustive behavioral checks.
 */
class PatientControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String phone = uniqueDigits(9);
        String email = "patient" + uniqueDigits(6) + "@example.com";

        mockMvc.perform(get("/api/v1/patients?sort=lastName,desc&status=active&gender=M")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"M\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\",\"address\":\"123 Main St\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String patientId = created.at("/data/patientId").asText();

        mockMvc.perform(get("/api/v1/patients/" + patientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"firstName\":\"Updated\",\"lastName\":\"Patient\",\"dob\":\"1990-01-01\","
                + "\"gender\":\"F\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\",\"address\":\"456 Side St\"}";
        mockMvc.perform(put("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/patients/" + patientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getPatient_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/patients/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
