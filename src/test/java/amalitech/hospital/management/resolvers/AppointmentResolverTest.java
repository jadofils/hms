package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.DoctorService;
import amalitech.hospital.management.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link AppointmentResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. Also exercises the hand-rolled {@code LocalDateTime} scalar
 *  registered by {@link GraphQlConfig}. */
@GraphQlTest(AppointmentResolver.class)
@Import(GraphQlConfig.class)
class AppointmentResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private PatientService patientService;
    @MockitoBean
    private DoctorService doctorService;

    private AppointmentResponse existingAppointment() {
        AppointmentResponse response = new AppointmentResponse();
        response.setAppointmentId("appt-1");
        response.setPatientId("patient-1");
        response.setDoctorId("doctor-1");
        response.setAppointmentDate(LocalDateTime.of(2099, 1, 1, 10, 0));
        response.setStatus("scheduled");
        return response;
    }

    @Test
    void appointment_returnsRealPatientAndDoctorObjects() {
        when(appointmentService.getAppointment("appt-1")).thenReturn(existingAppointment());
        PatientResponse patient = new PatientResponse();
        patient.setPatientId("patient-1");
        patient.setFirstName("Alice");
        when(patientService.getPatient("patient-1")).thenReturn(patient);
        DoctorResponse doctor = new DoctorResponse();
        doctor.setDoctorId("doctor-1");
        doctor.setFirstName("Greg");
        when(doctorService.getDoctor("doctor-1")).thenReturn(doctor);

        graphQlTester.document(
                        "{ appointment(appointmentId: \"appt-1\") { appointmentDate status patient { firstName } doctor { firstName } } }")
                .execute()
                .path("appointment.appointmentDate").entity(String.class).isEqualTo("2099-01-01T10:00")
                .path("appointment.patient.firstName").entity(String.class).isEqualTo("Alice")
                .path("appointment.doctor.firstName").entity(String.class).isEqualTo("Greg");
    }

    @Test
    void createAppointment_delegatesToService() {
        when(appointmentService.createAppointment(any())).thenReturn(existingAppointment());

        graphQlTester.document(
                        "mutation { createAppointment(input: { patientId: \"patient-1\", doctorId: \"doctor-1\", appointmentDate: \"2099-01-01T10:00:00\" }) { appointmentId } }")
                .execute()
                .path("createAppointment.appointmentId").entity(String.class).isEqualTo("appt-1");
    }

    @Test
    void deleteAppointment_returnsTrue() {
        graphQlTester.document("mutation { deleteAppointment(appointmentId: \"appt-1\") }")
                .execute()
                .path("deleteAppointment").entity(Boolean.class).isEqualTo(true);

        verify(appointmentService).deleteAppointment("appt-1");
    }
}
