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
 * Exercises {@link DoctorController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code DoctorServiceTest}); this only needs to reach
 * every controller method body (HTTP <-> DTO <-> service mapping), so assertions stay
 * light (status codes), not exhaustive behavioral checks.
 */
class DoctorControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String phone = uniqueDigits(9);
        String email = "doctor" + uniqueDigits(6) + "@example.com";

        mockMvc.perform(get("/api/v1/doctors?sort=lastName,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"firstName\":\"Test\",\"lastName\":\"Doctor\","
                + "\"specialization\":\"Cardiology\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String doctorId = created.at("/data/doctorId").asText();

        mockMvc.perform(get("/api/v1/doctors/" + doctorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"firstName\":\"Updated\",\"lastName\":\"Doctor\","
                + "\"specialization\":\"Neurology\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\"}";
        mockMvc.perform(put("/api/v1/doctors/" + doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/doctors/" + doctorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getDoctor_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/doctors/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignAndRemoveDepartment_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String phone = uniqueDigits(9);
        String email = "doctor" + uniqueDigits(6) + "@example.com";

        String createDoctorBody = "{\"firstName\":\"Dept\",\"lastName\":\"Doctor\","
                + "\"specialization\":\"Pediatrics\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\"}";
        MvcResult doctorResult = mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDoctorBody))
                .andExpect(status().isCreated())
                .andReturn();
        String doctorId = objectMapper.readTree(doctorResult.getResponse().getContentAsString())
                .at("/data/doctorId").asText();

        String deptName = "Test Department " + uniqueDigits(6);
        String createDeptBody = "{\"name\":\"" + deptName + "\",\"location\":\"Main Building\"}";
        MvcResult deptResult = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDeptBody))
                .andExpect(status().isCreated())
                .andReturn();
        String departmentId = objectMapper.readTree(deptResult.getResponse().getContentAsString())
                .at("/data/departmentId").asText();

        mockMvc.perform(post("/api/v1/doctors/" + doctorId + "/departments/" + departmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        MvcResult afterAssign = mockMvc.perform(get("/api/v1/doctors/" + doctorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode departments = objectMapper.readTree(afterAssign.getResponse().getContentAsString())
                .at("/data/departments");
        boolean found = false;
        for (JsonNode dept : departments) {
            if (departmentId.equals(dept.at("/departmentId").asText())) {
                found = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "assigned department should appear in doctor's departments");

        mockMvc.perform(delete("/api/v1/doctors/" + doctorId + "/departments/" + departmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
