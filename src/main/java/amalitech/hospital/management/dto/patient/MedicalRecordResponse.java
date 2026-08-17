package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDateTime;

/** Read-only projection of {@code MedicalRecord} — see {@link PatientAllergyResponse}'s
 *  Javadoc for why there's no request-DTO counterpart. */
@Data
public class MedicalRecordResponse {
    private String recordId;
    private String appointmentId;
    private String diagnosis;
    private String symptoms;
    private String notes;
    private LocalDateTime createdAt;
}
