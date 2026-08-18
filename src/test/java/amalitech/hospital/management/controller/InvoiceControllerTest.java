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
 * Exercises {@link InvoiceController} through real HTTP — the service layer already has
 * its own thorough unit tests ({@code InvoiceServiceTest}); this only needs to reach
 * every controller method body.
 */
class InvoiceControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String appointmentId = createAppointment(token);
        String patientId = createPatient(token);

        mockMvc.perform(get("/api/v1/invoices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"appointmentId\":\"" + appointmentId + "\",\"patientId\":\"" + patientId
                + "\",\"totalAmount\":150.00}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String invoiceId = created.at("/data/invoiceId").asText();

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"appointmentId\":\"" + appointmentId + "\",\"patientId\":\"" + patientId
                + "\",\"totalAmount\":150.00,\"paymentStatus\":\"paid\"}";
        mockMvc.perform(put("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/invoices/" + invoiceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getInvoice_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/invoices/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInvoice_returns404_whenAppointmentAbsent() throws Exception {
        String token = adminToken();
        String patientId = createPatient(token);
        String body = "{\"appointmentId\":\"nonexistent-id\",\"patientId\":\"" + patientId + "\"}";
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInvoices_filtersByPaymentStatus_throughRealHttpEndpoint() throws Exception {
        String token = adminToken();
        String appointmentId = createAppointment(token);
        String patientId = createPatient(token);
        String createBody = "{\"appointmentId\":\"" + appointmentId + "\",\"patientId\":\"" + patientId
                + "\",\"totalAmount\":150.00}";
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/invoices?paymentStatus=unpaid").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/invoices?paymentStatus=bogus").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
