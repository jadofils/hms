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
 * Exercises {@link DepartmentController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code DepartmentServiceTest}); this only needs to
 * reach every controller method body (HTTP <-> DTO <-> service mapping), so assertions
 * stay light (status codes), not exhaustive behavioral checks.
 */
class DepartmentControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String name = "Test Dept " + uniqueDigits(6);
        String phone = uniqueDigits(9);

        mockMvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"name\":\"" + name + "\",\"location\":\"Building A\",\"phone\":\"" + phone + "\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String departmentId = created.at("/data/departmentId").asText();

        mockMvc.perform(get("/api/v1/departments/" + departmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/departments/" + departmentId + "/doctors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"name\":\"" + name + " Updated\",\"location\":\"Building B\",\"phone\":\"" + phone + "\"}";
        mockMvc.perform(put("/api/v1/departments/" + departmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/departments/" + departmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getDepartment_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/departments/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
