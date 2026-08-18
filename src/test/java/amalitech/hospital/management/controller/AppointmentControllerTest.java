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
 * Exercises {@link AppointmentController} through real HTTP — the service layer already
 * has its own thorough unit tests ({@code AppointmentServiceTest}); this only needs to
 * reach every controller method body (HTTP <-> DTO <-> service mapping), so assertions
 * stay light (status codes), not exhaustive behavioral checks.
 */
class AppointmentControllerTest extends AbstractControllerTest {

    @Test
    void fullCrudLifecycle_throughRealHttpEndpoints() throws Exception {
        String token = adminToken();
        String patientId = createPatient(token);
        String doctorId = createDoctor(token);

        mockMvc.perform(get("/api/v1/appointments?sort=appointmentDate,desc&status=scheduled")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String createBody = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\","
                + "\"appointmentDate\":\"2028-06-15T10:00:00\",\"reason\":\"Checkup\",\"status\":\"scheduled\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String appointmentId = created.at("/data/appointmentId").asText();

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String updateBody = "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\","
                + "\"appointmentDate\":\"2028-06-15T10:00:00\",\"reason\":\"Follow-up\",\"status\":\"completed\"}";
        mockMvc.perform(put("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/appointments/" + appointmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAppointment_returns404_whenNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/appointments/nonexistent-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAppointment_returns404_whenPatientOrDoctorMissing() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);

        String createBody = "{\"patientId\":\"nonexistent-id\",\"doctorId\":\"" + doctorId + "\","
                + "\"appointmentDate\":\"2028-06-15T10:00:00\",\"reason\":\"Checkup\"}";
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAppointment_returns409_whenDoctorAlreadyBookedAtThatDateTime() throws Exception {
        String token = adminToken();
        String doctorId = createDoctor(token);
        String firstPatientId = createPatient(token);
        String secondPatientId = createPatient(token);
        String sharedSlot = "2029-03-10T09:00:00";

        String firstBody = "{\"patientId\":\"" + firstPatientId + "\",\"doctorId\":\"" + doctorId + "\","
                + "\"appointmentDate\":\"" + sharedSlot + "\",\"reason\":\"Checkup\"}";
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        String secondBody = "{\"patientId\":\"" + secondPatientId + "\",\"doctorId\":\"" + doctorId + "\","
                + "\"appointmentDate\":\"" + sharedSlot + "\",\"reason\":\"Different checkup\"}";
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict());
    }
}
