package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.patient.AppointmentRequest;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.patient.PatchAppointmentRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.DoctorService;
import amalitech.hospital.management.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link AppointmentService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs).
 *
 * <p>Unlike {@code AppointmentResponse}'s REST shape (flattened {@code patientName}/
 * {@code doctorName} strings), the GraphQL {@code Appointment} type exposes the real
 * {@code patient}/{@code doctor} objects — a caller selects exactly the related fields it
 * needs, resolved lazily here via {@link PatientService}/{@link DoctorService}.
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class AppointmentResolver {

    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final DoctorService doctorService;

    @QueryMapping
    public List<AppointmentResponse> appointments(@Argument int page, @Argument int size, @Argument String sort,
            @Argument String status) {
        return appointmentService.getAppointments(GraphQlPaging.of(page, size, sort), status).getContent();
    }

    @QueryMapping
    public AppointmentResponse appointment(@Argument String appointmentId) {
        return appointmentService.getAppointment(appointmentId);
    }

    @MutationMapping
    public AppointmentResponse createAppointment(@Argument @Valid AppointmentRequest input) {
        return appointmentService.createAppointment(input);
    }

    @MutationMapping
    public AppointmentResponse updateAppointment(@Argument String appointmentId, @Argument @Valid AppointmentRequest input) {
        return appointmentService.updateAppointment(appointmentId, input);
    }

    @MutationMapping
    public AppointmentResponse patchAppointment(@Argument String appointmentId, @Argument @Valid PatchAppointmentRequest input) {
        return appointmentService.patchAppointment(appointmentId, input);
    }

    @MutationMapping
    public boolean deleteAppointment(@Argument String appointmentId) {
        appointmentService.deleteAppointment(appointmentId);
        return true;
    }

    @SchemaMapping(typeName = "Appointment", field = "patient")
    public PatientResponse patient(AppointmentResponse appointment) {
        return patientService.getPatient(appointment.getPatientId());
    }

    @SchemaMapping(typeName = "Appointment", field = "doctor")
    public DoctorResponse doctor(AppointmentResponse appointment) {
        return doctorService.getDoctor(appointment.getDoctorId());
    }
}
