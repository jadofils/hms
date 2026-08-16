package amalitech.hospital.management.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link LabResultController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code LabResultServiceTest}); this only needs to
 * reach every controller method body.
 */
class LabResultControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String labOrderId = createLabOrder(token);
        String base = "/api/v1/lab-orders/" + labOrderId + "/result";

        String createBody = "{\"resultValue\":\"5.2\",\"unit\":\"mmol/L\",\"isAbnormal\":false}";
        mockMvc.perform(post(base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get(base).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"resultValue\":\"9.9\",\"unit\":\"mmol/L\",\"isAbnormal\":true}";
        mockMvc.perform(put(base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete(base).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getResult_returns404_whenLabOrderAbsent() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/lab-orders/nonexistent-id/result").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createResult_returns409_whenResultAlreadyExists() throws Exception {
        String token = adminToken();
        String labOrderId = createLabOrder(token);
        String base = "/api/v1/lab-orders/" + labOrderId + "/result";
        String body = "{\"resultValue\":\"5.2\"}";

        mockMvc.perform(post(base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
