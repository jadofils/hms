package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDateTime;

/** Read-only projection of {@code PatientNote} — see {@link PatientAllergyResponse}'s
 *  Javadoc for why there's no request-DTO counterpart. */
@Data
public class PatientNoteResponse {
    private String noteId;
    private String appointmentId;
    private String authorUserId;
    private String authorUsername;
    private String authorRole;
    private String noteText;
    private String source;
    private LocalDateTime createdAt;
}
