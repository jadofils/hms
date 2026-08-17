package amalitech.hospital.management.dto.patient;

import amalitech.hospital.management.dto.doctor.DoctorResponse;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentResponse {
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private LocalDateTime appointmentDate;
    private String status;
    private String reason;
    /** Not populated by the paginated listing or by create/update — only by the
     *  single-item lookup ({@code AppointmentService.getAppointment}), same convention
     *  as {@code DoctorResponse.departments}. Additive alongside the existing
     *  {@code patientId}/{@code patientName} scalars above, which are unchanged. */
    private PatientResponse patient;
    /** See {@link #patient}'s Javadoc — additive alongside {@code doctorId}/{@code doctorName}. */
    private DoctorResponse doctor;
}
